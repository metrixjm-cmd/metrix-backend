package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Request para crear un material de tipo LINK (sin subir archivo).
 */
@Data
public class CreateLinkMaterialRequest {

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String url;

    private String category;

    @Size(max = 10)
    private List<String> tags = new ArrayList<>();

    /** Null = material global. Provisto solo si el material es de una sucursal. */
    private String storeId;
}
