package com.metrix.api.service;

import com.metrix.api.dto.DailyReportResponse;

import java.time.LocalDate;

/**
 * Contrato del servicio de reportes de cierre diario para METRIX.
 * <p>
 * Sprint 8: genera reportes PDF (OpenPDF) y Excel (Apache POI)
 * filtrados por sucursal y fecha.
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
}
