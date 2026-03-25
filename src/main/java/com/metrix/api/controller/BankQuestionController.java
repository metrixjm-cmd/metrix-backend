package com.metrix.api.controller;

import com.metrix.api.dto.BankQuestionResponse;
import com.metrix.api.dto.CreateBankQuestionRequest;
import com.metrix.api.model.QuestionDifficulty;
import com.metrix.api.model.QuestionType;
import com.metrix.api.service.BankQuestionService;
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
 * API REST del banco de preguntas reutilizables (E3).
 * <p>
 * Base: /api/v1/question-bank
 * <p>
 * Roles:
 * <ul>
 *   <li>GET → cualquier autenticado</li>
 *   <li>POST / PUT → ADMIN, GERENTE</li>
 *   <li>DELETE → solo ADMIN</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/question-bank")
@RequiredArgsConstructor
@Tag(name = "Banco de Preguntas", description = "Preguntas reutilizables para construir exámenes (E3)")
public class BankQuestionController {

    private final BankQuestionService questionService;

    @Operation(summary = "Crear pregunta en el banco")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<BankQuestionResponse> create(
            @Valid @RequestBody CreateBankQuestionRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionService.create(request, auth.getName()));
    }

    @Operation(summary = "Listar preguntas del banco",
               description = "Paginado. Filtros: type, category, difficulty, tag, storeId.")
    @GetMapping
    public Page<BankQuestionResponse> list(
            @RequestParam(required = false) QuestionType       type,
            @RequestParam(required = false) String             category,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) String             tag,
            @RequestParam(required = false) String             storeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return questionService.list(type, category, difficulty, tag, storeId, page, size);
    }

    @Operation(summary = "Detalle de pregunta")
    @GetMapping("/{id}")
    public BankQuestionResponse getById(@PathVariable String id) {
        return questionService.getById(id);
    }

    @Operation(summary = "Tags disponibles para autocompletado", description = "Resultado cacheado.")
    @GetMapping("/tags")
    public List<String> getAllTags() {
        return questionService.getAllTags();
    }

    @Operation(summary = "Actualizar pregunta",
               description = "Solo permitido si usageCount == 0 (no está en uso en ningún examen).")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public BankQuestionResponse update(
            @PathVariable String id,
            @Valid @RequestBody CreateBankQuestionRequest request) {
        return questionService.update(id, request);
    }

    @Operation(summary = "Eliminar pregunta",
               description = "Soft-delete. Solo si usageCount == 0. Solo ADMIN.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        questionService.delete(id);
    }
}
