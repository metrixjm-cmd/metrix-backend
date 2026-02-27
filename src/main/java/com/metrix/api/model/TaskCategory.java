package com.metrix.api.model;

/**
 * Categorías operativas de tareas en METRIX.
 * <p>
 * Alimenta los dashboards especializados (Obj. #22):
 * <ul>
 *   <li>OPERACIONES  → Actividades de flujo diario: apertura, cierre, abastecimiento.</li>
 *   <li>RH           → Recursos Humanos: evaluaciones, altas/bajas, constancias.</li>
 *   <li>CAPACITACION → Certificaciones y programas de entrenamiento formal.</li>
 * </ul>
 */
public enum TaskCategory {
    OPERACIONES,
    RH,
    CAPACITACION
}
