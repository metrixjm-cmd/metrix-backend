package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Insignia de gamificación de un colaborador — Sprint 12.
 * <p>
 * Las insignias se calculan on-the-fly a partir de los KPIs del colaborador;
 * no se persisten en MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeDTO {

    /** Identificador del tipo de insignia. Uno de: PUNTUAL_ELITE, CERO_RETRABAJOS,
     *  VELOCIDAD_RAYO, COLABORADOR_MES, RACHA_7. */
    private String type;

    /** Nombre amigable mostrado en la UI. */
    private String title;

    /** Descripción del criterio para obtener la insignia. */
    private String description;

    /** Emoji representativo mostrado en la UI. */
    private String icon;

    /** Fecha aproximada en que se ganó la insignia (calculada al momento del request). */
    private Instant earnedAt;
}
