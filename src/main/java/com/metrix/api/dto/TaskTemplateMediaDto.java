package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Multimedia opcional asociada a una entrada de categorias. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateMediaDto {

    @NotBlank(message = "El tipo de multimedia es obligatorio")
    private String type;

    @NotBlank(message = "La URL de multimedia es obligatoria")
    private String url;

    private String title;
    private String description;
}
