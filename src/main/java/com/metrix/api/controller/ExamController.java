package com.metrix.api.controller;

import com.metrix.api.dto.*;
import com.metrix.api.service.ExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para el módulo Trainer (Sprint 19).
 * <p>
 * Endpoints:
 * <ul>
 *   <li>POST /api/v1/exams                          — crear examen (solo ADMIN)</li>
 *   <li>GET  /api/v1/exams/store/{id}               — listar exámenes de sucursal</li>
 *   <li>GET  /api/v1/exams/{id}                     — detalle con respuestas correctas (ADMIN/GERENTE)</li>
 *   <li>GET  /api/v1/exams/{id}/take                — examen para responder (sin respuestas)</li>
 *   <li>POST /api/v1/exams/{id}/submit              — enviar respuestas</li>
 *   <li>GET  /api/v1/exams/{id}/submissions         — historial (ADMIN/GERENTE)</li>
 *   <li>GET  /api/v1/exams/my-submissions           — mis submissions</li>
 *   <li>POST /api/v1/exams/from-template/{id}       — crear desde plantilla (solo ADMIN)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@Tag(name = "Exámenes", description = "Módulo Trainer — exámenes y calificación automática (Sprint 19)")
public class ExamController {

    private final ExamService examService;

    /** Crear examen — solo ADMIN. */
    @Operation(summary = "Crear examen", description = "Crea un nuevo examen con preguntas y respuestas. Solo ADMIN.")
    @ApiResponse(responseCode = "201", description = "Examen creado exitosamente")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExamResponse> create(
            @Valid @RequestBody CreateExamRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examService.create(request, auth.getName()));
    }

    /** Listar exámenes de una sucursal. */
    @Operation(summary = "Exámenes por sucursal", description = "Lista todos los exámenes asociados a una sucursal. Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "200", description = "Lista de exámenes de la sucursal")
    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<List<ExamResponse>> getByStore(@PathVariable String storeId) {
        return ResponseEntity.ok(examService.getByStore(storeId));
    }

    /** Detalle completo (incluye respuestas correctas) — solo ADMIN/GERENTE. */
    @Operation(summary = "Detalle de examen (con respuestas)", description = "Obtiene el detalle completo del examen incluyendo las respuestas correctas. Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "200", description = "Detalle completo del examen")
    @GetMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<ExamResponse> getById(@PathVariable String examId) {
        return ResponseEntity.ok(examService.getById(examId));
    }

    /** Vista del examen para responder — sin respuestas correctas. Cualquier usuario autenticado. */
    @Operation(summary = "Obtener examen para responder (sin respuestas)", description = "Devuelve el examen sin las respuestas correctas, listo para que el colaborador lo responda.")
    @ApiResponse(responseCode = "200", description = "Examen listo para responder")
    @GetMapping("/{examId}/take")
    public ResponseEntity<ExamForTakeResponse> getForTake(@PathVariable String examId) {
        return ResponseEntity.ok(examService.getForTake(examId));
    }

    /** Enviar respuestas y obtener calificación. Cualquier usuario autenticado. */
    @Operation(summary = "Enviar respuestas y obtener calificación", description = "Envía las respuestas del colaborador y recibe la calificación automática.")
    @ApiResponse(responseCode = "201", description = "Respuestas evaluadas y calificación generada")
    @PostMapping("/{examId}/submit")
    public ResponseEntity<ExamSubmissionResponse> submit(
            @PathVariable String examId,
            @Valid @RequestBody SubmitExamRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examService.submit(examId, request, auth.getName()));
    }

    /** Historial de submissions de un examen — solo ADMIN/GERENTE. */
    @Operation(summary = "Historial de submissions", description = "Lista todas las submissions (intentos) de un examen. Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "200", description = "Lista de submissions del examen")
    @GetMapping("/{examId}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<List<ExamSubmissionResponse>> getSubmissions(@PathVariable String examId) {
        return ResponseEntity.ok(examService.getSubmissions(examId));
    }

    /** Mis propias submissions. Cualquier usuario autenticado. */
    @Operation(summary = "Mis submissions", description = "Lista las submissions (intentos) del usuario autenticado en todos los exámenes.")
    @ApiResponse(responseCode = "200", description = "Lista de submissions del usuario")
    @GetMapping("/my-submissions")
    public ResponseEntity<List<ExamSubmissionResponse>> getMySubmissions(Authentication auth) {
        return ResponseEntity.ok(examService.getMySubmissions(auth.getName()));
    }

    /** Estadísticas avanzadas del examen — ADMIN/GERENTE. */
    @Operation(summary = "Estadísticas del examen",
               description = "Distribución de puntajes, tasa de fallo por pregunta, tiempos y pendientes de revisión.")
    @GetMapping("/{examId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<ExamStatsResponse> getStats(@PathVariable String examId) {
        return ResponseEntity.ok(examService.getStats(examId));
    }

    /** Información de intentos del usuario actual sobre un examen. */
    @Operation(summary = "Información de intentos",
               description = "Devuelve cuántos intentos lleva el usuario y si puede volver a intentar.")
    @GetMapping("/{examId}/attempt-info")
    public ResponseEntity<AttemptInfoResponse> getAttemptInfo(
            @PathVariable String examId,
            Authentication auth) {
        return ResponseEntity.ok(examService.getAttemptInfo(examId, auth.getName()));
    }

    /** Actualizar examen — solo ADMIN/GERENTE. */
    @Operation(summary = "Actualizar examen", description = "Actualiza título, descripción, preguntas y configuración de un examen. Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "200", description = "Examen actualizado")
    @PutMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<ExamResponse> update(
            @PathVariable String examId,
            @Valid @RequestBody CreateExamRequest request) {
        return ResponseEntity.ok(examService.update(examId, request));
    }

    /** Eliminar examen (soft delete) — solo ADMIN/GERENTE. */
    @Operation(summary = "Eliminar examen", description = "Desactiva un examen (soft delete). Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "204", description = "Examen eliminado")
    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Void> delete(@PathVariable String examId) {
        examService.delete(examId);
        return ResponseEntity.noContent().build();
    }

    /** Crear examen desde plantilla — preguntas copiadas como snapshot. Solo ADMIN. */
    @Operation(summary = "Crear examen desde plantilla",
               description = "Crea un Exam usando una ExamTemplate como base. Las preguntas se copian como snapshot inmutable. Solo ADMIN.")
    @PostMapping("/from-template/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExamResponse> createFromTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody CreateExamFromTemplateRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examService.createFromTemplate(templateId, request, auth.getName()));
    }
}
