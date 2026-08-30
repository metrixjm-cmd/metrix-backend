package com.metrix.api.dto;

import com.metrix.api.model.LicenseAccent;
import com.metrix.api.model.LicensePricingModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateLicensePackageRequest {

    @NotBlank
    @Size(max = 120)
    private String nombre;

    @Size(max = 160)
    private String etiqueta;

    @NotBlank
    @Size(max = 500)
    private String descripcion;

    @NotBlank
    private String moneda;

    @NotNull
    private LicensePricingModel pricingModel;

    @NotNull
    private BigDecimal precioMensual;

    @NotNull
    private BigDecimal precioAnual;

    @NotNull
    private BigDecimal precioImplementacion;

    private boolean precioPersonalizado;

    private Integer minUsuarios;
    private Integer maxUsuarios;
    private Integer minSucursales;
    private Integer maxSucursales;

    @Size(max = 200)
    private String soporte;

    @Valid
    @NotNull
    @Size(min = 1)
    private List<LicenseFeatureDto> funciones;

    @NotNull
    private LicenseAccent accent;

    private boolean destacado;
    private boolean activo;
}
