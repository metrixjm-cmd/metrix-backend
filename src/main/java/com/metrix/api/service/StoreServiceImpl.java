package com.metrix.api.service;

import com.metrix.api.dto.CreateStoreRequest;
import com.metrix.api.dto.StoreResponse;
import com.metrix.api.dto.UpdateStoreRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.Store;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementación del módulo de Sucursales — Sprint 11.
 * <p>
 * Cada respuesta se enriquece con conteos de usuarios, tareas y capacitaciones
 * activas para la sucursal, permitiendo al frontend mostrar estadísticas sin
 * llamadas adicionales.
 */
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository    storeRepository;
    private final UserRepository     userRepository;
    private final TaskRepository     taskRepository;
    private final TrainingRepository trainingRepository;
    private final SequenceService    sequenceService;

    // ── Crear ────────────────────────────────────────────────────────────────

    @Override
    public StoreResponse create(CreateStoreRequest request, String createdBy) {
        // Auto-generar código si no se envía
        String codigo = request.getCodigo();
        if (codigo == null || codigo.isBlank()) {
            codigo = sequenceService.generateStoreCode();
        } else {
            codigo = codigo.toUpperCase();
        }

        if (storeRepository.existsByCodigo(codigo)) {
            throw new IllegalStateException(
                    "Ya existe una sucursal con el código: " + codigo);
        }

        Store.StoreBuilder builder = Store.builder()
                .nombre(request.getNombre())
                .codigo(codigo)
                .createdBy(createdBy);

        if (request.getDireccion() != null) builder.direccion(request.getDireccion());
        if (request.getTelefono()  != null) builder.telefono(request.getTelefono());
        if (request.getTurnos()    != null && !request.getTurnos().isEmpty()) {
            builder.turnos(request.getTurnos());
        }

        return toResponse(storeRepository.save(builder.build()));
    }

    // ── Listar ───────────────────────────────────────────────────────────────

    @Override
    public List<StoreResponse> getAll() {
        List<Store> stores = storeRepository.findByActivoTrue();
        if (stores.isEmpty()) return List.of();

        // Batch-load counts for ALL stores in 3 queries (instead of 3 × N)
        List<String> storeIds = stores.stream().map(Store::getId).toList();
        Map<String, Long> userCounts = countByStoreIds(
                userRepository.findByActivoTrue(), com.metrix.api.model.User::getStoreId, storeIds);
        Map<String, Long> taskCounts = countByStoreIds(
                taskRepository.findByActivoTrue(), com.metrix.api.model.Task::getStoreId, storeIds);
        Map<String, Long> trainingCounts = countByStoreIds(
                trainingRepository.findByActivoTrue(), t -> t.getStoreId(), storeIds);

        return stores.stream()
                .map(s -> toResponse(s,
                        userCounts.getOrDefault(s.getId(), 0L),
                        taskCounts.getOrDefault(s.getId(), 0L),
                        trainingCounts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    /** Groups entities by storeId and counts per store — eliminates N+1. */
    private <T> Map<String, Long> countByStoreIds(
            List<T> entities, java.util.function.Function<T, String> storeIdExtractor,
            List<String> storeIds) {
        return entities.stream()
                .filter(e -> storeIdExtractor.apply(e) != null)
                .collect(Collectors.groupingBy(storeIdExtractor, Collectors.counting()));
    }

    // ── Detalle ──────────────────────────────────────────────────────────────

    @Override
    public StoreResponse getById(String id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada: " + id));
        return toResponse(store);
    }

    // ── Editar ───────────────────────────────────────────────────────────────

    @Override
    public StoreResponse update(String id, UpdateStoreRequest request) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada: " + id));

        if (request.getNombre()    != null && !request.getNombre().isBlank()) {
            store.setNombre(request.getNombre());
        }
        if (request.getDireccion() != null) {
            store.setDireccion(request.getDireccion());
        }
        if (request.getTelefono()  != null) {
            store.setTelefono(request.getTelefono());
        }
        if (request.getTurnos()    != null && !request.getTurnos().isEmpty()) {
            store.setTurnos(request.getTurnos());
        }

        return toResponse(storeRepository.save(store));
    }

    // ── Desactivar (soft-delete) ──────────────────────────────────────────────

    @Override
    public void deactivate(String id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada: " + id));

        if (!store.isActivo()) {
            throw new IllegalStateException("La sucursal ya está inactiva.");
        }

        store.setActivo(false);
        storeRepository.save(store);
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    /** Single store detail — 3 count queries (acceptable for detail view). */
    private StoreResponse toResponse(Store store) {
        String storeId = store.getId();
        return toResponse(store,
                userRepository.countByStoreIdAndActivoTrue(storeId),
                taskRepository.countByStoreIdAndActivoTrue(storeId),
                trainingRepository.countByStoreIdAndActivoTrue(storeId));
    }

    /** Batch-friendly mapper with pre-computed counts (eliminates N+1 in getAll). */
    private StoreResponse toResponse(Store store, long totalUsers, long totalTasks, long totalTrainings) {
        return StoreResponse.builder()
                .id(store.getId())
                .nombre(store.getNombre())
                .codigo(store.getCodigo())
                .direccion(store.getDireccion())
                .telefono(store.getTelefono())
                .turnos(store.getTurnos())
                .activo(store.isActivo())
                .createdBy(store.getCreatedBy())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .totalUsers(totalUsers)
                .totalTasks(totalTasks)
                .totalTrainings(totalTrainings)
                .build();
    }
}
