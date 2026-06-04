package com.metrix.api.model;

public enum QuestionType {
    /** Verdadero / Falso — radio button, 1 correcta. */
    TRUE_FALSE,
    /** Selección múltiple — checkboxes, N correctas, scoring parcial proporcional. */
    MULTI_SELECT
}
