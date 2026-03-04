package com.metrix.api.controller;

import com.metrix.api.dto.DailyReportResponse;
import com.metrix.api.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controller REST de Reportes de Cierre Diario — Sprint 8.
 * <p>
 * Base path: {@code /api/v1/reports}
 * <p>
 * Matriz de acceso:
 * <pre>
 *   GET /daily           → ADMIN, GERENTE  (JSON preview)
 *   GET /daily/pdf       → ADMIN, GERENTE  (descarga PDF)
 *   GET /daily/excel     → ADMIN, GERENTE  (descarga XLSX)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /api/v1/reports/daily?storeId=&date=YYYY-MM-DD
     * Preview del reporte como JSON para mostrar en la UI antes de descargar.
     */
    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<DailyReportResponse> getDailyReport(
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.buildDailyReport(storeId, date));
    }

    /**
     * GET /api/v1/reports/daily/pdf?storeId=&date=YYYY-MM-DD
     * Descarga del reporte como archivo PDF.
     */
    @GetMapping("/daily/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<byte[]> getDailyPdf(
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailyReportResponse report = reportService.buildDailyReport(storeId, date);
        byte[] pdf = reportService.generatePdf(report);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"cierre-" + date + ".pdf\"")
                .body(pdf);
    }

    /**
     * GET /api/v1/reports/daily/excel?storeId=&date=YYYY-MM-DD
     * Descarga del reporte como archivo Excel (XLSX).
     */
    @GetMapping("/daily/excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<byte[]> getDailyExcel(
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailyReportResponse report = reportService.buildDailyReport(storeId, date);
        byte[] excel = reportService.generateExcel(report);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"cierre-" + date + ".xlsx\"")
                .body(excel);
    }

    /**
     * GET /api/v1/reports/user/{userId}/performance-card
     * <p>
     * Descarga la Ficha de Desempeño Individual de un colaborador en PDF.
     * Incluye datos personales, KPIs acumulados e insignias de gamificación.
     * Sprint 12.
     */
    @GetMapping("/user/{userId}/performance-card")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<byte[]> getPerformanceCard(@PathVariable String userId) {
        byte[] pdf = reportService.generatePerformanceCard(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ficha-desempeno-" + userId + ".pdf\"")
                .body(pdf);
    }
}
