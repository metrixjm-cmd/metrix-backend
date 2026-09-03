package com.metrix.api.dto.productos;

import com.metrix.api.model.LicenseAccent;
import com.metrix.api.model.LicensePricingModel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductOrderPackageSnapshotResponse {

    private String packageId;
    private String nombre;
    private String etiqueta;
    private LicensePricingModel pricingModel;
    private BigDecimal precioMensual;
    private BigDecimal precioImplementacion;
    private String moneda;
    private Integer maxUsuarios;
    private Integer maxSucursales;
    private Integer diasPrueba;
    private LicenseAccent accent;
}
