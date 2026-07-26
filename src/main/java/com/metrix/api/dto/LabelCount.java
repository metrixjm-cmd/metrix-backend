package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Par etiqueta-conteo genérico para alimentar gráficas de distribución
 * (donuts, barras) en los KPIs agregados por dominio.
 *
 * @see IncidentKpiResponse
 * @see TrainingKpiResponse
 * @see ExamKpiResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelCount {
    /** Etiqueta legible o nombre del enum (ej: "CRITICA", "0–49", "EQUIPO"). */
    private String label;
    /** Conteo absoluto de elementos en esta categoría. */
    private long count;
    /** Porcentaje del total (0–100), redondeado. */
    private int percentage;
}
