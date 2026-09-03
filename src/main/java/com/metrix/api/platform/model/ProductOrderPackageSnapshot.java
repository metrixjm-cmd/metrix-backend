package com.metrix.api.platform.model;

import com.metrix.api.model.LicenseAccent;
import com.metrix.api.model.LicensePricingModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/** Snapshot inmutable del paquete al momento de la compra. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOrderPackageSnapshot {

    @Field("package_id")
    private String packageId;

    @Field("nombre")
    private String nombre;

    @Field("etiqueta")
    private String etiqueta;

    @Field("pricing_model")
    private LicensePricingModel pricingModel;

    @Field("precio_mensual")
    private BigDecimal precioMensual;

    @Field("precio_implementacion")
    private BigDecimal precioImplementacion;

    @Field("moneda")
    private String moneda;

    @Field("max_usuarios")
    private Integer maxUsuarios;

    @Field("max_sucursales")
    private Integer maxSucursales;

    /** Códigos de módulo congelados al comprar (TRAININGS, EXAMS, …). */
    @Field("feature_codes")
    private java.util.List<String> featureCodes;

    @Field("accent")
    private LicenseAccent accent;
}
