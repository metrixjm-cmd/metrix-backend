package com.metrix.api.service;

import com.metrix.api.dto.DailyReportResponse;

import java.time.LocalDate;

/**
 * Contrato del servicio de reportes para METRIX.
 * <p>
 * Sprint 8: reportes de cierre diario PDF + Excel.
 * Sprint 12: ficha de desempeño individual por colaborador.
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
}
