package com.metrix.api.dto;

import com.metrix.api.model.LicenseAccent;
import com.metrix.api.model.LicensePricingModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicensePackageResponse {

    private String id;
    private String nombre;
    private String etiqueta;
    private String descripcion;
    private String moneda;
    private LicensePricingModel pricingModel;
    private BigDecimal precioMensual;
    private BigDecimal precioAnual;
    private BigDecimal precioImplementacion;
    private boolean precioPersonalizado;
    private Integer minUsuarios;
    private Integer maxUsuarios;
    private Integer minSucursales;
    private Integer maxSucursales;
    private String soporte;
    private List<LicenseFeatureDto> funciones;
    private List<String> featureCodes;
    private int diasPrueba;
    private LicenseAccent accent;
    private boolean destacado;
    private boolean activo;
}
