package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Paquete comercial METRIX administrable desde el módulo Licencias.
 * <p>
 * El {@link #id} es un slug estable ({@code base}, {@code pro}, …) para
 * enlazar rutas del frontend sin depender del ObjectId de MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "license_packages")
public class LicensePackage {

    @Id
    private String id;

    @Version
    private Long version;

    @Field("nombre")
    private String nombre;

    @Field("etiqueta")
    private String etiqueta;

    @Field("descripcion")
    private String descripcion;

    @Builder.Default
    @Field("moneda")
    private String moneda = "MXN";

    @Field("pricing_model")
    private LicensePricingModel pricingModel;

    @Field("precio_mensual")
    private BigDecimal precioMensual;

    @Field("precio_anual")
    private BigDecimal precioAnual;

    @Field("precio_implementacion")
    private BigDecimal precioImplementacion;

    @Builder.Default
    @Field("precio_personalizado")
    private boolean precioPersonalizado = false;

    @Field("min_usuarios")
    private Integer minUsuarios;

    @Field("max_usuarios")
    private Integer maxUsuarios;

    @Field("min_sucursales")
    private Integer minSucursales;

    @Field("max_sucursales")
    private Integer maxSucursales;

    @Field("soporte")
    private String soporte;

    @Field("funciones")
    private List<LicenseFeature> funciones;

    /**
     * Módulos licenciables: {@code TRAININGS}, {@code EXAMS}, {@code GAMIFICATION}, {@code API}.
     * Independiente de las etiquetas de marketing en {@link #funciones}.
     */
    @Field("feature_codes")
    private List<String> featureCodes;

    /** Días de prueba al contratar. {@code null} = 7; {@code 0} = sin prueba. */
    @Builder.Default
    @Field("dias_prueba")
    private Integer diasPrueba = 7;

    @Field("accent")
    private LicenseAccent accent;

    @Builder.Default
    @Field("destacado")
    private boolean destacado = false;

    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
