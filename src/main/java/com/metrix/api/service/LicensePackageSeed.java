package com.metrix.api.service;

import com.metrix.api.model.LicenseAccent;
import com.metrix.api.model.LicenseFeature;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.model.LicensePricingModel;
import com.metrix.api.platform.license.LicenseFeatureCodes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catálogo inicial de paquetes comerciales METRIX.
 */
public final class LicensePackageSeed {

    private LicensePackageSeed() {}

    public static List<LicensePackage> defaults() {
        return List.of(
                base(),
                pro(),
                expansion(),
                prime()
        );
    }

    private static LicensePackage base() {
        return LicensePackage.builder()
                .id("base")
                .nombre("METRIX Base")
                .etiqueta("Hasta 15 usuarios y 1-2 sucursales")
                .descripcion("Operación de un punto de venta: tareas del turno, incidencias con evidencia y métricas básicas. Contratación mensual sin plazo forzoso.")
                .moneda("MXN")
                .pricingModel(LicensePricingModel.PER_BRANCH)
                .precioMensual(bd(1999))
                .precioAnual(BigDecimal.ZERO)
                .precioImplementacion(bd(1797))
                .maxUsuarios(15)
                .minSucursales(1)
                .maxSucursales(2)
                .soporte("Correo, respuesta en 24 h")
                .funciones(List.of(
                        feature("Tareas y checklists operativos", true),
                        feature("Panel de métricas (KPIs)", true),
                        feature("Incidencias con evidencia fotográfica", true),
                        feature("Notificación en tiempo real - tareas realizadas y pendientes", true)
                ))
                .featureCodes(List.of())
                .accent(LicenseAccent.slate)
                .destacado(false)
                .activo(true)
                .build();
    }

    private static LicensePackage pro() {
        return LicensePackage.builder()
                .id("pro")
                .nombre("METRIX Pro")
                .etiqueta("Hasta 50 usuarios y 3-5 sucursales")
                .descripcion("Para cadenas en crecimiento: suma capacitaciones, exámenes y gamificación sobre la operación diaria.")
                .moneda("MXN")
                .pricingModel(LicensePricingModel.FLAT_MONTHLY)
                .precioMensual(bd(3599))
                .precioAnual(BigDecimal.ZERO)
                .precioImplementacion(bd(3597))
                .maxUsuarios(50)
                .minSucursales(3)
                .maxSucursales(5)
                .soporte("Correo respuesta 24 h")
                .funciones(List.of(
                        feature("Tareas y checklist operativos", true),
                        feature("Panel de métricas (KPIs)", true),
                        feature("Incidencias con evidencia fotográfica", true),
                        feature("Capacitaciones y exámenes", true),
                        feature("Gamificación y ranking", true),
                        feature("Notificaciones en tiempo Real - Tareas realizadas y pendientes", true)
                ))
                .featureCodes(List.of(
                        LicenseFeatureCodes.TRAININGS,
                        LicenseFeatureCodes.EXAMS,
                        LicenseFeatureCodes.GAMIFICATION
                ))
                .accent(LicenseAccent.cyan)
                .destacado(true)
                .activo(true)
                .build();
    }

    private static LicensePackage expansion() {
        return LicensePackage.builder()
                .id("expansion")
                .nombre("METRIX Expansión")
                .etiqueta("De 101 a 200 usuarios, hasta 20 sucursales")
                .descripcion("Operación multi-sucursal con reportes PDF automáticos y soporte prioritario.")
                .moneda("MXN")
                .pricingModel(LicensePricingModel.PER_USER)
                .precioMensual(bd(69))
                .precioAnual(BigDecimal.ZERO)
                .precioImplementacion(bd(8997))
                .minUsuarios(101)
                .maxUsuarios(200)
                .maxSucursales(20)
                .soporte("Correo respuesta 8 h")
                .funciones(List.of(
                        feature("Tareas y checklist operativos", true),
                        feature("Panel de métricas (KPIs)", true),
                        feature("Incidencias con evidencia fotográfica", true),
                        feature("Capacitaciones y Exámenes", true),
                        feature("Gamificación y Ranking", true),
                        feature("Reportes PDF automáticos", true),
                        feature("Notificaciones en tiempo real - todas -", true)
                ))
                .featureCodes(List.of(
                        LicenseFeatureCodes.TRAININGS,
                        LicenseFeatureCodes.EXAMS,
                        LicenseFeatureCodes.GAMIFICATION
                ))
                .accent(LicenseAccent.violet)
                .destacado(false)
                .activo(true)
                .build();
    }

    private static LicensePackage prime() {
        return LicensePackage.builder()
                .id("prime")
                .nombre("METRIX Prime")
                .etiqueta("De 250 a 400 usuarios, hasta 40 sucursales")
                .descripcion("Para cadenas nacionales: integración API, gerente de cuenta dedicado y SLA por contrato.")
                .moneda("MXN")
                .pricingModel(LicensePricingModel.PER_USER)
                .precioMensual(bd(65))
                .precioAnual(BigDecimal.ZERO)
                .precioImplementacion(bd(9997))
                .minUsuarios(250)
                .maxUsuarios(400)
                .maxSucursales(40)
                .soporte("Gerente de cuenta dedicado")
                .funciones(List.of(
                        feature("Tareas y checklists operativos", true),
                        feature("Panel de métricas (KPIs)", true),
                        feature("Incidencias con evidencia fotográfica", true),
                        feature("Capacitaciones y exámenes", true),
                        feature("Gamificación y ranking", true),
                        feature("Reportes PDF automáticos", true),
                        feature("Notificaciones en tiempo real - todas -", true),
                        feature("API de integración", true),
                        feature("Gerente de cuenta dedicado", true)
                ))
                .featureCodes(List.of(
                        LicenseFeatureCodes.TRAININGS,
                        LicenseFeatureCodes.EXAMS,
                        LicenseFeatureCodes.GAMIFICATION,
                        LicenseFeatureCodes.API
                ))
                .accent(LicenseAccent.amber)
                .destacado(false)
                .activo(true)
                .build();
    }

    private static LicenseFeature feature(String label, boolean incluido) {
        return LicenseFeature.builder().label(label).incluido(incluido).build();
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
