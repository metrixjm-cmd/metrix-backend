package com.metrix.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request para que ADMIN/GERENTE apruebe o rechace respuestas OPEN_TEXT
 * pendientes de revisión manual en una submission.
 */
@Data
public class ReviewOpenTextRequest {

    @NotNull
    private List<ReviewItem> reviews;

    @Data
    public static class ReviewItem {
        /** Índice de la pregunta dentro de questionResults (0-based). */
        private int questionIndex;

        /** true = aprobar respuesta, false = rechazar. */
        private boolean approved;
    }
}
