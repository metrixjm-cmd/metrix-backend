package com.metrix.api.controller;

import com.metrix.api.dto.CreateTrainingRequest;
import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingProgressRequest;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints del módulo de Capacitación — Sprint 10.
 *
 * GET    /api/v1/trainings/my              → mis capacitaciones       (any auth)
 * GET    /api/v1/trainings/store/{storeId} → por sucursal             (ADMIN, GERENTE)
 * GET    /api/v1/trainings/{id}            → detalle                  (any auth)
 * POST   /api/v1/trainings                 → crear                    (ADMIN, GERENTE)
 * PATCH  /api/v1/trainings/{id}/progress   → actualizar progreso      (any auth)
 * DELETE /api/v1/trainings/{id}            → soft-delete              (ADMIN)
 */
@RestController
@RequestMapping("/api/v1/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;
    private final UserRepository  userRepository;

    // ── Mis capacitaciones ───────────────────────────────────────────────

    @GetMapping("/my")
    public ResponseEntity<List<TrainingResponse>> getMyTrainings(Authentication auth) {
        String userId = resolveUserId(auth.getName());
        return ResponseEntity.ok(trainingService.getMyTrainings(userId));
    }

    // ── Por sucursal (gerencial) ─────────────────────────────────────────

    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<TrainingResponse>> getByStore(@PathVariable String storeId) {
        return ResponseEntity.ok(trainingService.getByStore(storeId));
    }

    // ── Detalle ──────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<TrainingResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(trainingService.getById(id));
    }

    // ── Crear ────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TrainingResponse> create(
            @Valid @RequestBody CreateTrainingRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trainingService.create(request, auth.getName()));
    }

    // ── Actualizar Progreso ──────────────────────────────────────────────

    @PatchMapping("/{id}/progress")
    public ResponseEntity<TrainingResponse> updateProgress(
            @PathVariable String id,
            @Valid @RequestBody UpdateTrainingProgressRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                trainingService.updateProgress(id, request, auth.getName()));
    }

    // ── Soft-delete ──────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        trainingService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Helper: resuelve MongoDB _id desde numeroUsuario ─────────────────

    private String resolveUserId(String numeroUsuario) {
        return userRepository.findByNumeroUsuario(numeroUsuario)
                .map(u -> u.getId())
                .orElse(numeroUsuario);
    }
}
