package com.metrix.api.controller;

import com.metrix.api.dto.CreateLinkMaterialRequest;
import com.metrix.api.dto.TrainingMaterialResponse;
import com.metrix.api.model.MaterialType;
import com.metrix.api.service.TrainingMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * API REST del banco de materiales de capacitación.
 * <p>
 * Base: /api/v1/training-materials
 * <p>
 * Roles:
 * <ul>
 *   <li>GET → cualquier autenticado</li>
 *   <li>POST / DELETE → ADMIN, GERENTE</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/training-materials")
@RequiredArgsConstructor
@Tag(name = "Banco de Materiales", description = "Repositorio central de materiales reutilizables para capacitaciones")
public class TrainingMaterialController {

    private final TrainingMaterialService materialService;

    // ── Upload de archivo (PDF / VIDEO / IMAGE) ────────────────────────────

    @Operation(summary = "Subir material (archivo)",
               description = "Sube un archivo al banco. Soporta PDF, imágenes (JPG, PNG, WebP) y videos (MP4, WebM). ADMIN/GERENTE.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TrainingMaterialResponse> uploadFile(
            @RequestParam("file")                       MultipartFile file,
            @RequestParam("title")                      String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category",    required = false) String category,
            @RequestParam(value = "tags",        required = false) String tagsRaw,
            @RequestParam(value = "storeId",     required = false) String storeId,
            Authentication auth) {

        List<String> tags = (tagsRaw != null && !tagsRaw.isBlank())
                ? Arrays.stream(tagsRaw.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList()
                : List.of();

        TrainingMaterialResponse response = materialService.uploadFile(
                file, title, description, category, tags, storeId, auth.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Crear material tipo LINK (sin archivo) ─────────────────────────────

    @Operation(summary = "Crear material tipo LINK",
               description = "Registra una URL externa como material. ADMIN/GERENTE.")
    @PostMapping("/link")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TrainingMaterialResponse> createLink(
            @Valid @RequestBody CreateLinkMaterialRequest request,
            Authentication auth) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(materialService.createLink(request, auth.getName()));
    }

    // ── Listado paginado con filtros ───────────────────────────────────────

    @Operation(summary = "Listar materiales",
               description = "Listado paginado. Filtros: type, category, tag, storeId. Cualquier autenticado.")
    @GetMapping
    public Page<TrainingMaterialResponse> list(
            @RequestParam(required = false) MaterialType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String storeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return materialService.list(type, category, tag, storeId, page, size);
    }

    // ── Detalle ────────────────────────────────────────────────────────────

    @Operation(summary = "Detalle de material")
    @GetMapping("/{id}")
    public TrainingMaterialResponse getById(@PathVariable String id) {
        return materialService.getById(id);
    }

    // ── Tags disponibles ───────────────────────────────────────────────────

    @Operation(summary = "Obtener todos los tags",
               description = "Lista de tags únicos para autocompletado en el formulario.")
    @GetMapping("/tags")
    public List<String> getAllTags() {
        return materialService.getAllTags();
    }

    // ── Soft-delete ────────────────────────────────────────────────────────

    @Operation(summary = "Eliminar material",
               description = "Soft-delete. Falla si el material tiene usageCount > 0. Solo ADMIN.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        materialService.delete(id);
    }
}
