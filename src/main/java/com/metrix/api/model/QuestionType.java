package com.metrix.api.model;

public enum QuestionType {
    /** Opción única — radio buttons (1 correcta). Antes llamado MULTIPLE_CHOICE. */
    MULTIPLE_CHOICE,
    /** Opción única — Verdadero / Falso. */
    TRUE_FALSE,
    /** Selección múltiple — checkboxes (N correctas, scoring parcial proporcional). */
    MULTI_SELECT,
    /** Respuesta libre — keyword matching + bandera para revisión manual. */
    OPEN_TEXT
}
