package com.metrix.api.service;

import com.metrix.api.dto.DailyReportResponse;
import com.metrix.api.dto.EmployeesReportResponse;
import com.metrix.api.dto.ManagersReportResponse;

import java.time.LocalDate;

/**
 * Contrato del servicio de reportes para METRIX.
 * <p>
 * Sprint 8: reportes de cierre diario PDF + Excel.
 * Sprint 12: ficha de desempeño individual por colaborador.
 * Sprint 18: reportes de ranking gerencial y de colaboradores (PDF).
 */
public interface ReportService {

    /**
     * Ensambla los datos del reporte para una sucursal en una fecha específica.
     * Filtra tareas por {@code createdAt} dentro del día UTC solicitado.
     */
    DailyReportResponse buildDailyReport(String storeId, LocalDate date);

    /**
     * Genera un PDF con: tabla KPIs, tabla tareas, tabla ranking colaboradores.
     * Usa OpenPDF (com.lowagie.text).
     */
    byte[] generatePdf(DailyReportResponse report);

    /**
     * Genera un XLSX con 3 hojas: "Resumen KPIs", "Tareas", "Colaboradores".
     * Usa Apache POI (org.apache.poi.xssf.usermodel).
     */
    byte[] generateExcel(DailyReportResponse report);

    /**
     * Genera una Ficha de Desempeño Individual en PDF para un colaborador.
     * Incluye: datos personales, KPIs acumulados, insignias de gamificación.
     * Sprint 12.
     */
    byte[] generatePerformanceCard(String userId);

    // ── Sprint 18: reportes de ranking ────────────────────────────────────
    // Ambos se apoyan en los leaderboards de gamificación, que se calculan
    // sobre una ventana móvil desde hoy. Por eso reciben `period` y no una
    // fecha: un día calendario no describiría los datos que devuelven.

    /**
     * Ranking gerencial de toda la cadena, ordenado por IGEO de equipo.
     *
     * @param period "weekly" (7 días) o "monthly" (30 días)
     */
    ManagersReportResponse buildManagersReport(String period);

    /** Genera el PDF del ranking gerencial. */
    byte[] generateManagersPdf(ManagersReportResponse report);

    /**
     * Ranking de colaboradores de una sucursal, ordenado por IGEO.
     *
     * @param storeId sucursal a reportar
     * @param period  "weekly" (7 días) o "monthly" (30 días)
     */
    EmployeesReportResponse buildEmployeesReport(String storeId, String period);

    /** Genera el PDF del ranking de colaboradores. */
    byte[] generateEmployeesPdf(EmployeesReportResponse report);
}
