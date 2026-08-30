package com.metrix.api.dto.productos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProductOrderRequest {

    @NotBlank
    private String packageId;

    @NotBlank
    private String empresaNombre;

    @NotBlank
    private String contactoNombre;

    @NotBlank
    @Email
    private String contactoEmail;

    private String contactoTelefono;

    @NotNull
    @Min(1)
    private Integer sucursalesContratadas;
}
