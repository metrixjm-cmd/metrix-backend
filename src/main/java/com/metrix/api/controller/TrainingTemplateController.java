package com.metrix.api.controller;

import com.metrix.api.dto.CreateTrainingTemplateRequest;
import com.metrix.api.dto.TrainingTemplateResponse;
import com.metrix.api.model.TrainingLevel;
import com.metrix.api.service.TrainingTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST de plantillas de capacitación reutilizables.
 * <p>
 * Base: /api/v1/training-templates
 */
@RestController
@RequestMapping("/api/v1/training-templates")
@RequiredArgsConstructor
@Tag(name = "Plantillas de Capacitación", description = "Plantillas reutilizables para crear capacitaciones")
public class TrainingTemplateController {

    private final TrainingTemplateService templateService;

    @Operation(summary = "Crear plantilla")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TrainingTemplateResponse> create(
            @Valid @RequestBody CreateTrainingTemplateRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.create(request, auth.getName()));
    }

    @Operation(summary = "Listar plantillas", description = "Paginado. Filtros: category, level, tag.")
    @GetMapping
    public Page<TrainingTemplateResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TrainingLevel level,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return templateService.list(category, level, tag, page, size);
    }

    @Operation(summary = "Listado resumido para selector", description = "Solo id, title, category, level, timesUsed. Sin paginación.")
    @GetMapping("/summaries")
    public List<TrainingTemplateResponse> getSummaries() {
        return templateService.getSummaries();
    }

    @Operation(summary = "Detalle de plantilla", description = "Incluye materiales resueltos con URL y metadata.")
    @GetMapping("/{id}")
    public TrainingTemplateResponse getById(@PathVariable String id) {
        return templateService.getById(id);
    }

    @Operation(summary = "Actualizar plantilla")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public TrainingTemplateResponse update(
            @PathVariable String id,
            @Valid @RequestBody CreateTrainingTemplateRequest request,
            Authentication auth) {
        return templateService.update(id, request, auth.getName());
    }

    @Operation(summary = "Eliminar plantilla", description = "Soft-delete. Decrementa usageCount de materiales. Solo ADMIN.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        templateService.delete(id);
    }
}
