package com.metrix.api.service;

import com.metrix.api.dto.CreateIncidentRequest;
import com.metrix.api.dto.IncidentResponse;
import com.metrix.api.dto.UpdateIncidentStatusRequest;
import com.metrix.api.event.DomainEvents.IncidentCreatedEvent;
import com.metrix.api.event.DomainEvents.IncidentStatusChangedEvent;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.IncidentRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de incidencias operativas para METRIX (Sprint 15).
 * <p>
 * Cubre el Objetivo #20 — Gestión de Contingencias.
 * <p>
 * Ciclo de vida: ABIERTA → EN_RESOLUCION → CERRADA (re-apertura: CERRADA → ABIERTA).
 * Las incidencias CRITICA disparan SSE inmediato a los gerentes de la sucursal.
 */
@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository        incidentRepository;
    private final UserRepository            userRepository;
    private final GcsService                gcsService;
    private final ApplicationEventPublisher eventPublisher;

    // ── Crear ────────────────────────────────────────────────────────────────

    @Override
    public IncidentResponse create(CreateIncidentRequest request, String reporterNumeroUsuario) {
        User reporter = userRepository.findByNumeroUsuario(reporterNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario reportador no encontrado: " + reporterNumeroUsuario));

        String primaryRole = reporter.getRoles().contains(Role.ADMIN) ? "ADMIN"
                : reporter.getRoles().contains(Role.GERENTE) ? "GERENTE" : "EJECUTADOR";

        Incident incident = Incident.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .severity(request.getSeverity())
                .taskId(request.getTaskId())
                .reporterUserId(reporter.getId())
                .reporterName(reporter.getNombre())
                .reporterPosition(reporter.getPuesto())
                .reporterRole(primaryRole)
                .storeId(request.getStoreId())
                .shift(request.getShift())
                .implicados(request.getImplicados() != null
                        ? new ArrayList<>(request.getImplicados())
                        : new ArrayList<>())
                .followUpResponsible(request.getFollowUpResponsible())
                .build();

        Incident saved = incidentRepository.save(incident);

        // Emit domain event — IncidentEventListener handles SSE notification
        eventPublisher.publishEvent(new IncidentCreatedEvent(
                saved.getId(), saved.getStoreId(), saved.getReporterUserId(),
                saved.getTitle(), saved.getReporterName(), saved.getShift(),
                saved.getSeverity() != null ? saved.getSeverity().name() : "MEDIA"));

        return toResponse(saved);
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Override
    public List<IncidentResponse> getMyIncidents(String reporterNumeroUsuario) {
        User reporter = userRepository.findByNumeroUsuario(reporterNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + reporterNumeroUsuario));
        return incidentRepository.findByReporterUserIdAndActivoTrue(reporter.getId())
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<IncidentResponse> getByStore(String storeId) {
        return incidentRepository.findByStoreIdAndActivoTrue(storeId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<IncidentResponse> getVisibleForUser(String currentNumeroUsuario) {
        User current = userRepository.findByNumeroUsuario(currentNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + currentNumeroUsuario));

        if (current.getRoles().contains(Role.ADMIN)) {
            // ADMIN: ve incidencias de GERENTES (todas las sucursales) + las suyas propias
            List<Incident> byRole = incidentRepository
                    .findByActivoTrueAndReporterRoleIn(List.of("GERENTE"));
            List<Incident> own = incidentRepository
                    .findByReporterUserIdAndActivoTrue(current.getId());
            return mergeAndDeduplicate(byRole, own);

        } else if (current.getRoles().contains(Role.GERENTE)) {
            // GERENTE: ve incidencias de EJECUTADORES de su sucursal + las suyas propias
            List<Incident> byRole = incidentRepository
                    .findByStoreIdAndActivoTrueAndReporterRoleIn(current.getStoreId(), List.of("EJECUTADOR"));
            List<Incident> own = incidentRepository
                    .findByReporterUserIdAndActivoTrue(current.getId());
            return mergeAndDeduplicate(byRole, own);

        } else {
            // EJECUTADOR: solo las suyas
            return incidentRepository.findByReporterUserIdAndActivoTrue(current.getId())
                    .stream().map(this::toResponse).toList();
        }
    }

    private List<IncidentResponse> mergeAndDeduplicate(List<Incident> listA, List<Incident> listB) {
        java.util.Map<String, Incident> map = new java.util.LinkedHashMap<>();
        for (Incident i : listA) map.put(i.getId(), i);
        for (Incident i : listB) map.putIfAbsent(i.getId(), i);
        return map.values().stream().map(this::toResponse).toList();
    }

    @Override
    public List<IncidentResponse> getByStoreAndStatus(String storeId, IncidentStatus status) {
        return incidentRepository.findByStoreIdAndStatusAndActivoTrue(storeId, status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public IncidentResponse getById(String incidentId) {
        return incidentRepository.findById(incidentId)
                .filter(Incident::isActivo)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Incidencia no encontrada: " + incidentId));
    }

    // ── Cambio de Estado ─────────────────────────────────────────────────────

    @Override
    public IncidentResponse updateStatus(String incidentId, UpdateIncidentStatusRequest request,
                                          String currentNumeroUsuario) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(Incident::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Incidencia no encontrada: " + incidentId));

        IncidentStatus current = incident.getStatus();
        IncidentStatus next    = request.getNewStatus();

        validateTransition(current, next);

        Instant now = Instant.now();

        switch (next) {
            case EN_RESOLUCION -> applyInResolution(incident);
            case CERRADA       -> applyClosed(incident, now, request.getResolutionNotes(),
                                             request.getClosedByName(), currentNumeroUsuario);
            case ABIERTA       -> applyReopened(incident);
        }

        incident.getTransitions().add(IncidentTransition.builder()
                .fromStatus(current)
                .toStatus(next)
                .changedAt(now)
                .changedBy(currentNumeroUsuario)
                .notes(request.getNotes())
                .build());

        Incident saved = incidentRepository.save(incident);

        // Emit domain event — IncidentEventListener handles SSE notification
        eventPublisher.publishEvent(new IncidentStatusChangedEvent(
                saved.getId(), current, next, saved.getStoreId(),
                saved.getReporterUserId(), saved.getTitle(),
                saved.getResolutionNotes()));

        return toResponse(saved);
    }

    // ── Lógica de Transiciones ────────────────────────────────────────────────

    private void validateTransition(IncidentStatus current, IncidentStatus next) {
        boolean valid = switch (current) {
            case ABIERTA       -> next == IncidentStatus.EN_RESOLUCION;
            case EN_RESOLUCION -> next == IncidentStatus.CERRADA;
            case CERRADA       -> next == IncidentStatus.ABIERTA;
        };

        if (!valid) {
            throw new IllegalStateException(String.format(
                    "Transición inválida: %s → %s. " +
                    "Flujo permitido: ABIERTA→EN_RESOLUCION, EN_RESOLUCION→CERRADA, CERRADA→ABIERTA.",
                    current, next));
        }
    }

    private void applyInResolution(Incident incident) {
        incident.setStatus(IncidentStatus.EN_RESOLUCION);
    }

    private void applyClosed(Incident incident, Instant now, String resolutionNotes,
                              String closedByNameOverride, String resolvedBy) {
        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            throw new IllegalStateException(
                    "Al cerrar una incidencia debe proporcionar 'resolutionNotes' con el detalle de la resolución.");
        }
        // Si el front envía un nombre explícito, usarlo. Si no, obtenerlo del usuario autenticado.
        String closerName;
        String closerNumero;
        if (closedByNameOverride != null && !closedByNameOverride.isBlank()) {
            closerName   = closedByNameOverride.trim();
            closerNumero = resolvedBy;
        } else {
            User closer = userRepository.findByNumeroUsuario(resolvedBy).orElse(null);
            closerName   = closer != null ? closer.getNombre()        : resolvedBy;
            closerNumero = closer != null ? closer.getNumeroUsuario() : resolvedBy;
        }

        incident.setStatus(IncidentStatus.CERRADA);
        incident.setResolutionNotes(resolutionNotes);
        incident.setResolvedByUserId(resolvedBy);
        incident.setClosedByName(closerName);
        incident.setClosedByNumero(closerNumero);
        incident.setResolvedAt(now);
    }

    private void applyReopened(Incident incident) {
        incident.setStatus(IncidentStatus.ABIERTA);
        incident.setResolvedAt(null);
        incident.setResolvedByUserId(null);
        incident.setClosedByName(null);
        incident.setClosedByNumero(null);
        incident.setResolutionNotes(null);
    }

    // ── Evidencias Multimedia ─────────────────────────────────────────────────

    @Override
    public IncidentResponse uploadEvidence(String incidentId, MultipartFile file,
                                            String currentNumeroUsuario) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(Incident::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Incidencia no encontrada: " + incidentId));

        String rawContentType = file.getContentType();
        String contentType = rawContentType != null ? rawContentType : "";
        String tipo;
        String extension;

        if (contentType.startsWith("image/")) {
            tipo      = "img";
            extension = contentType.equals("image/png") ? "png"
                      : contentType.equals("image/webp") ? "webp" : "jpg";
        } else if (contentType.startsWith("video/")) {
            tipo      = "vid";
            extension = contentType.equals("video/webm") ? "webm" : "mp4";
        } else {
            throw new IllegalArgumentException(
                    "Tipo de archivo no soportado. Se aceptan imágenes (JPG, PNG, WebP) y videos (MP4, WebM).");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo de evidencia.", e);
        }

        String url = gcsService.uploadFile(
                incident.getStoreId(), incidentId, tipo, bytes, contentType, extension);

        incident.getEvidenceUrls().add(url);
        return toResponse(incidentRepository.save(incident));
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private IncidentResponse toResponse(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .version(incident.getVersion())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .category(incident.getCategory())
                .severity(incident.getSeverity())
                .taskId(incident.getTaskId())
                .reporterUserId(incident.getReporterUserId())
                .reporterName(incident.getReporterName())
                .reporterPosition(incident.getReporterPosition())
                .reporterRole(incident.getReporterRole())
                .storeId(incident.getStoreId())
                .shift(incident.getShift())
                .implicados(incident.getImplicados())
                .followUpResponsible(incident.getFollowUpResponsible())
                .status(incident.getStatus())
                .resolvedByUserId(incident.getResolvedByUserId())
                .closedByName(incident.getClosedByName())
                .closedByNumero(incident.getClosedByNumero())
                .resolutionNotes(incident.getResolutionNotes())
                .resolvedAt(incident.getResolvedAt())
                .evidenceUrls(incident.getEvidenceUrls())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }

    @Override
    public void deleteAll() {
        incidentRepository.deleteAll();
    }
}
