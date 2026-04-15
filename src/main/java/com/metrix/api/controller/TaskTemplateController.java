package com.metrix.api.controller;

import com.metrix.api.dto.TaskTemplateRequest;
import com.metrix.api.dto.TaskTemplateResponse;
import com.metrix.api.dto.TaskTemplateMediaDto;
import com.metrix.api.dto.TaskTemplateStepDto;
import com.metrix.api.model.TaskTemplate;
import com.metrix.api.model.TaskTemplateMedia;
import com.metrix.api.model.TaskTemplateStep;
import com.metrix.api.repository.TaskTemplateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API de Plantillas de Tarea — "Banco de Tareas".
 * <p>
 * GET  /api/v1/task-templates?q={query} → buscar plantillas activas por título
 * GET  /api/v1/task-templates            → listar todas las plantillas activas
 * POST /api/v1/task-templates            → crear nueva plantilla (ADMIN/GERENTE)
 * PUT  /api/v1/task-templates/{id}       → actualizar plantilla (ADMIN/GERENTE)
 * DELETE /api/v1/task-templates/{id}     → desactivar plantilla (ADMIN/GERENTE)
 */
@RestController
@RequestMapping({"/api/v1/categorias", "/api/v1/task-templates"})
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Catalogo enriquecido para pre-rellenar el formulario de creacion de tareas")
public class TaskTemplateController {

    private final TaskTemplateRepository templateRepository;

    // ── Lectura ──────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "Listar o buscar plantillas de tarea",
            description = "Sin parámetro devuelve todas las activas. Con 'q' filtra por título (case-insensitive, máx 20 resultados).")
    @ApiResponse(responseCode = "200", description = "Lista de plantillas")
    public ResponseEntity<List<TaskTemplateResponse>> getTemplates(
            @Parameter(description = "Texto de búsqueda sobre el título de la plantilla")
            @RequestParam(required = false) String q) {

        List<TaskTemplate> templates;
        if (StringUtils.hasText(q)) {
            templates = templateRepository.findTop20ByTitleContainingIgnoreCaseAndActivoTrue(q.trim());
        } else {
            templates = templateRepository.findByActivoTrueOrderByTitleAsc();
        }

        return ResponseEntity.ok(templates.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una plantilla por id")
    @ApiResponse(responseCode = "200", description = "Plantilla encontrada")
    @ApiResponse(responseCode = "404", description = "No encontrada o inactiva")
    public ResponseEntity<TaskTemplateResponse> getById(@PathVariable String id) {
        return templateRepository.findById(id)
                .filter(TaskTemplate::isActivo)
                .map(t -> ResponseEntity.ok(toResponse(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Escritura (ADMIN / GERENTE) ───────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @Operation(summary = "Crear plantilla de tarea")
    @ApiResponse(responseCode = "201", description = "Plantilla creada")
    @ApiResponse(responseCode = "409", description = "Ya existe una plantilla con ese título")
    public ResponseEntity<TaskTemplateResponse> create(
            @Valid @RequestBody TaskTemplateRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        if (templateRepository.existsByTitleIgnoreCaseAndActivoTrue(request.getTitle().trim())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        TaskTemplate template = TaskTemplate.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory().trim())
                .steps(toStepEntities(request.getSteps()))
                .media(toMediaEntities(request.getMedia()))
                .createdBy(principal != null ? principal.getUsername() : null)
                .creatorName(principal != null ? principal.getUsername() : null)
                .build();

        TaskTemplate saved = templateRepository.save(template);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @Operation(summary = "Actualizar plantilla de tarea")
    @ApiResponse(responseCode = "200", description = "Plantilla actualizada")
    @ApiResponse(responseCode = "404", description = "No encontrada")
    public ResponseEntity<TaskTemplateResponse> update(
            @PathVariable String id,
            @Valid @RequestBody TaskTemplateRequest request) {

        return templateRepository.findById(id)
                .filter(TaskTemplate::isActivo)
                .map(t -> {
                    t.setTitle(request.getTitle().trim());
                    t.setDescription(request.getDescription().trim());
                    t.setCategory(request.getCategory().trim());
                    t.setSteps(toStepEntities(request.getSteps()));
                    t.setMedia(toMediaEntities(request.getMedia()));
                    return ResponseEntity.ok(toResponse(templateRepository.save(t)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @Operation(summary = "Desactivar plantilla (soft-delete)")
    @ApiResponse(responseCode = "204", description = "Plantilla desactivada")
    @ApiResponse(responseCode = "404", description = "No encontrada")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        var template = templateRepository.findById(id);
        if (template.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TaskTemplate t = template.get();
        t.setActivo(false);
        templateRepository.save(t);
        return ResponseEntity.noContent().build();
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private TaskTemplateResponse toResponse(TaskTemplate t) {
        List<TaskTemplateStepDto> stepDtos = t.getSteps() == null
                ? List.of()
                : t.getSteps().stream().map(s -> TaskTemplateStepDto.builder()
                        .title(s.getTitle())
                        .description(s.getDescription())
                        .tags(s.getTags())
                        .order(s.getOrder())
                        .build())
                .collect(Collectors.toList());

        List<TaskTemplateMediaDto> mediaDtos = t.getMedia() == null
                ? List.of()
                : t.getMedia().stream().map(m -> TaskTemplateMediaDto.builder()
                        .type(m.getType())
                        .url(m.getUrl())
                        .title(m.getTitle())
                        .description(m.getDescription())
                        .build())
                .collect(Collectors.toList());

        return TaskTemplateResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .category(t.getCategory())
                .steps(stepDtos)
                .media(mediaDtos)
                .timesUsed(t.getTimesUsed())
                .creatorName(t.getCreatorName())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private List<TaskTemplateStep> toStepEntities(List<TaskTemplateStepDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(d -> TaskTemplateStep.builder()
                        .title(d.getTitle())
                        .description(d.getDescription())
                        .tags(d.getTags() != null ? d.getTags() : List.of())
                        .order(d.getOrder())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TaskTemplateMedia> toMediaEntities(List<TaskTemplateMediaDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(d -> TaskTemplateMedia.builder()
                        .type(d.getType())
                        .url(d.getUrl())
                        .title(d.getTitle())
                        .description(d.getDescription())
                        .build())
                .collect(Collectors.toList());
    }
}
