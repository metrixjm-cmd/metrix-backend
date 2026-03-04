package com.metrix.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO que mapea exactamente la respuesta JSON del endpoint
 * {@code GET /api/v1/analytics/igeo} del microservicio Python (analytics-service).
 * <p>
 * Usa Java 21 records: inmutables, sin boilerplate, deserializables por Jackson.
 * Los campos snake_case del JSON se mapean con {@code @JsonProperty}.
 * <p>
 * Estructura del JSON de origen:
 * <pre>
 * {
 *   "status":      "ok",
 *   "metric":      "igeo",
 *   "description": "...",
 *   "weights":     { "cumplimiento": 0.40, "tiempo": 0.25, "calidad": 0.20, "consistencia": 0.15 },
 *   "computed_at": "2026-02-28T06:45:00.000000+00:00",
 *   "data": {
 *     "global": {
 *       "total_tasks": 75, "completed": 45,
 *       "pillar_scores": { "cumplimiento": 60.0, "tiempo": 71.11, "calidad": 83.60, "consistencia": 77.78 },
 *       "igeo": 69.97
 *     },
 *     "by_store": [
 *       { "store_id": "abc123", "total_tasks": 25, "completed": 16,
 *         "pillar_scores": { ... }, "igeo": 73.53 }
 *     ]
 *   }
 * }
 * </pre>
 */
public record IgeoAnalyticsResponse(

        String status,
        String metric,
        String description,
        Weights weights,

        @JsonProperty("computed_at")
        String computedAt,

        AnalyticsData data

) {

    // ── Ponderaciones de los 4 pilares ────────────────────────────────────────

    public record Weights(
            double cumplimiento,
            double tiempo,
            double calidad,
            double consistencia
    ) {}

    // ── Contenedor data.global + data.by_store ────────────────────────────────

    public record AnalyticsData(

            GlobalResult global,

            @JsonProperty("by_store")
            List<StoreResult> byStore

    ) {}

    // ── data.global ───────────────────────────────────────────────────────────

    public record GlobalResult(

            @JsonProperty("total_tasks")
            int totalTasks,

            int completed,

            @JsonProperty("pillar_scores")
            PillarScores pillarScores,

            double igeo

    ) {}

    // ── Elemento de data.by_store ─────────────────────────────────────────────

    public record StoreResult(

            @JsonProperty("store_id")
            String storeId,

            @JsonProperty("total_tasks")
            int totalTasks,

            int completed,

            @JsonProperty("pillar_scores")
            PillarScores pillarScores,

            double igeo

    ) {}

    // ── Scores por pilar (shared por GlobalResult y StoreResult) ─────────────

    public record PillarScores(
            double cumplimiento,
            double tiempo,
            double calidad,
            double consistencia
    ) {}
}
