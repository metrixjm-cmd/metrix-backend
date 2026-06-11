package com.metrix.api.model;

public enum QuestionType {
    /** Respuesta única — radio button, 3 opciones, 1 correcta. */
    SINGLE_SELECT,
    /** Verdadero / Falso — radio button, 1 correcta. */
    TRUE_FALSE,
    /** Selección múltiple — checkboxes, N correctas, scoring parcial proporcional. */
    MULTI_SELECT
}
