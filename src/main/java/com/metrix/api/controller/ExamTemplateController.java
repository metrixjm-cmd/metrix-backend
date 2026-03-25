package com.metrix.api.controller;

import com.metrix.api.dto.CreateExamTemplateRequest;
import com.metrix.api.dto.ExamTemplateResponse;
import com.metrix.api.service.ExamTemplateService;
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
 * API REST de plantillas de examen reutilizables (E4).
 * <p>
 * Base: /api/v1/exam-templates
 */
@RestController
@RequestMapping("/api/v1/exam-templates")
@RequiredArgsConstructor
@Tag(name = "Plantillas de Examen", description = "Plantillas reutilizables que agrupan preguntas del banco (E4)")
public class ExamTemplateController {

    private final ExamTemplateService templateService;

    @Operation(summary = "Crear plantilla de examen")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ExamTemplateResponse> create(
            @Valid @RequestBody CreateExamTemplateRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.create(request, auth.getName()));
    }

    @Operation(summary = "Listar plantillas", description = "Paginado. Filtros: category, tag, storeId.")
    @GetMapping
    public Page<ExamTemplateResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String storeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return templateService.list(category, tag, storeId, page, size);
    }

    @Operation(summary = "Listado resumido para selector", description = "Solo metadata esencial. Sin paginación. Resultado cacheado.")
    @GetMapping("/summaries")
    public List<ExamTemplateResponse> getSummaries() {
        return templateService.getSummaries();
    }

    @Operation(summary = "Detalle de plantilla", description = "Incluye preguntas resueltas del banco.")
    @GetMapping("/{id}")
    public ExamTemplateResponse getById(@PathVariable String id) {
        return templateService.getById(id);
    }

    @Operation(summary = "Actualizar plantilla")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ExamTemplateResponse update(
            @PathVariable String id,
            @Valid @RequestBody CreateExamTemplateRequest request) {
        return templateService.update(id, request);
    }

    @Operation(summary = "Eliminar plantilla", description = "Soft-delete. Solo ADMIN.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        templateService.delete(id);
    }
}
