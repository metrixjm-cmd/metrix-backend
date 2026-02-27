package com.metrix.api.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.metrix.api.dto.*;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de reportes de cierre diario — Sprint 8.
 * <p>
 * Genera reportes PDF con OpenPDF y Excel con Apache POI.
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final TaskRepository taskRepository;
    private final KpiService     kpiService;

    // ── buildDailyReport ─────────────────────────────────────────────────

    @Override
    public DailyReportResponse buildDailyReport(String storeId, LocalDate date) {
        // Ventana UTC del día solicitado
        var startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endOfDay   = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Task> allForStore = taskRepository.findByStoreIdAndActivoTrue(storeId);
        List<Task> dayTasks = allForStore.stream()
                .filter(t -> t.getCreatedAt() != null
                        && !t.getCreatedAt().isBefore(startOfDay)
                        && t.getCreatedAt().isBefore(endOfDay))
                .collect(Collectors.toList());

        // KPIs del día (reutiliza el summary del store completo para simplificar)
        KpiSummaryResponse kpiSummary = kpiService.getStoreSummary(storeId);

        // Colaboradores del store
        List<UserResponsibilityResponse> userRanking = kpiService.getUsersResponsibility(storeId);

        // Velocidad de corrección del store
        CorrectionSpeedResponse correctionSpeed = kpiService.getCorrectionSpeed(storeId);

        List<TaskResponse> taskResponses = dayTasks.stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());

        long completed = dayTasks.stream()
                .filter(t -> t.getExecution().getStatus() == TaskStatus.COMPLETED).count();
        long failed    = dayTasks.stream()
                .filter(t -> t.getExecution().getStatus() == TaskStatus.FAILED).count();
        long pending   = dayTasks.stream()
                .filter(t -> t.getExecution().getStatus() == TaskStatus.PENDING).count();

        return DailyReportResponse.builder()
                .storeId(storeId)
                .reportDate(date)
                .kpiSummary(kpiSummary)
                .tasks(taskResponses)
                .userRanking(userRanking)
                .correctionSpeed(correctionSpeed)
                .totalAssigned(dayTasks.size())
                .totalCompleted((int) completed)
                .totalFailed((int) failed)
                .totalPending((int) pending)
                .build();
    }

    // ── generatePdf ──────────────────────────────────────────────────────

    @Override
    public byte[] generatePdf(DailyReportResponse report) {
        try (var baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Encabezado ──
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(234, 88, 12));
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font cellFont  = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(28, 25, 23));
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(28, 25, 23));

            doc.add(new Paragraph("METRIX — Reporte de Cierre Diario", titleFont));
            doc.add(new Paragraph("Sucursal: " + report.getStoreId()
                    + "   |   Fecha: " + report.getReportDate(), cellFont));
            doc.add(new Paragraph("Tareas del día: " + report.getTotalAssigned()
                    + "  Completadas: " + report.getTotalCompleted()
                    + "  Fallidas: " + report.getTotalFailed()
                    + "  Pendientes: " + report.getTotalPending(), cellFont));
            doc.add(Chunk.NEWLINE);

            // ── Tabla KPI Summary ──
            doc.add(new Paragraph("Resumen de KPIs", sectionFont));
            doc.add(Chunk.NEWLINE);
            KpiSummaryResponse kpi = report.getKpiSummary();
            if (kpi != null) {
                PdfPTable kpiTable = new PdfPTable(2);
                kpiTable.setWidthPercentage(60);
                addHeaderCell(kpiTable, "KPI", headerFont);
                addHeaderCell(kpiTable, "Valor", headerFont);
                addKpiRow(kpiTable, "IGEO", formatKpi(kpi.getIgeo()), cellFont);
                addKpiRow(kpiTable, "On-Time Rate", formatKpiPct(kpi.getOnTimeRate()), cellFont);
                addKpiRow(kpiTable, "Re-trabajo", formatKpiPct(kpi.getReworkRate()), cellFont);
                addKpiRow(kpiTable, "Críticas Pendientes", String.valueOf(kpi.getCriticalPending()), cellFont);
                addKpiRow(kpiTable, "Avg. Ejecución (min)", formatKpi(kpi.getAvgExecutionMinutes()), cellFont);
                doc.add(kpiTable);
            }
            doc.add(Chunk.NEWLINE);

            // ── Tabla Tareas del día ──
            doc.add(new Paragraph("Tareas del Día", sectionFont));
            doc.add(Chunk.NEWLINE);
            if (report.getTasks() != null && !report.getTasks().isEmpty()) {
                PdfPTable tasksTable = new PdfPTable(5);
                tasksTable.setWidthPercentage(100);
                tasksTable.setWidths(new float[]{3f, 1.5f, 1.2f, 1.2f, 1f});
                for (String h : new String[]{"Título", "Categoría", "Estado", "Turno", "A tiempo"}) {
                    addHeaderCell(tasksTable, h, headerFont);
                }
                for (TaskResponse t : report.getTasks()) {
                    addCell(tasksTable, t.getTitle(), cellFont);
                    addCell(tasksTable, t.getCategory() != null ? t.getCategory().name() : "-", cellFont);
                    addCell(tasksTable, t.getStatus() != null ? t.getStatus().name() : "-", cellFont);
                    addCell(tasksTable, t.getShift() != null ? t.getShift() : "-", cellFont);
                    addCell(tasksTable, t.getOnTime() != null ? (t.getOnTime() ? "Sí" : "No") : "-", cellFont);
                }
                doc.add(tasksTable);
            } else {
                doc.add(new Paragraph("Sin tareas registradas para esta fecha.", cellFont));
            }
            doc.add(Chunk.NEWLINE);

            // ── Tabla Colaboradores (KPI #7) ──
            doc.add(new Paragraph("Ranking de Colaboradores", sectionFont));
            doc.add(Chunk.NEWLINE);
            if (report.getUserRanking() != null && !report.getUserRanking().isEmpty()) {
                PdfPTable usersTable = new PdfPTable(7);
                usersTable.setWidthPercentage(100);
                usersTable.setWidths(new float[]{0.5f, 2f, 1.5f, 1f, 1f, 1f, 1f});
                for (String h : new String[]{"#", "Nombre", "Puesto", "Total", "Complet.", "On-Time%", "IGEO"}) {
                    addHeaderCell(usersTable, h, headerFont);
                }
                for (UserResponsibilityResponse u : report.getUserRanking()) {
                    addCell(usersTable, String.valueOf(u.getRank()), cellFont);
                    addCell(usersTable, u.getNombre() != null ? u.getNombre() : "-", cellFont);
                    addCell(usersTable, u.getPosition() != null ? u.getPosition() : "-", cellFont);
                    addCell(usersTable, String.valueOf(u.getTotalTasks()), cellFont);
                    addCell(usersTable, String.valueOf(u.getCompletedTasks()), cellFont);
                    addCell(usersTable, formatKpiPct(u.getOnTimeRate()), cellFont);
                    addCell(usersTable, formatKpi(u.getIgeo()), cellFont);
                }
                doc.add(usersTable);
            } else {
                doc.add(new Paragraph("Sin colaboradores registrados.", cellFont));
            }

            // ── KPI #9 — Velocidad de Corrección ──
            CorrectionSpeedResponse cs = report.getCorrectionSpeed();
            if (cs != null && cs.getReworkedTasks() > 0) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("Velocidad de Corrección (KPI #9)", sectionFont));
                doc.add(Chunk.NEWLINE);
                PdfPTable csTable = new PdfPTable(2);
                csTable.setWidthPercentage(50);
                addHeaderCell(csTable, "Métrica", headerFont);
                addHeaderCell(csTable, "Valor", headerFont);
                addKpiRow(csTable, "Tareas con rework", String.valueOf(cs.getReworkedTasks()), cellFont);
                addKpiRow(csTable, "Tiempo promedio (min)", formatKpi(cs.getAvgCorrectionMinutes()), cellFont);
                addKpiRow(csTable, "Tiempo mínimo (min)", formatKpi(cs.getMinCorrectionMinutes()), cellFont);
                addKpiRow(csTable, "Tiempo máximo (min)", formatKpi(cs.getMaxCorrectionMinutes()), cellFont);
                doc.add(csTable);
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    // ── generateExcel ────────────────────────────────────────────────────

    @Override
    public byte[] generateExcel(DailyReportResponse report) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             var baos = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle   = createDataStyle(wb);

            // ── Sheet 1: Resumen KPIs ──
            Sheet kpiSheet = wb.createSheet("Resumen KPIs");
            String[] kpiHeaders = {"KPI", "Valor"};
            createRow(kpiSheet, 0, kpiHeaders, headerStyle);
            KpiSummaryResponse kpi = report.getKpiSummary();
            if (kpi != null) {
                int r = 1;
                createDataRow(kpiSheet, r++, dataStyle, "IGEO", formatKpi(kpi.getIgeo()));
                createDataRow(kpiSheet, r++, dataStyle, "On-Time Rate (%)", formatKpiPct(kpi.getOnTimeRate()));
                createDataRow(kpiSheet, r++, dataStyle, "Re-trabajo (%)", formatKpiPct(kpi.getReworkRate()));
                createDataRow(kpiSheet, r++, dataStyle, "Críticas Pendientes", String.valueOf(kpi.getCriticalPending()));
                createDataRow(kpiSheet, r++, dataStyle, "Avg. Ejecución (min)", formatKpi(kpi.getAvgExecutionMinutes()));
                createDataRow(kpiSheet, r++, dataStyle, "Sucursal", report.getStoreId());
                createDataRow(kpiSheet, r++, dataStyle, "Fecha", report.getReportDate().toString());
                createDataRow(kpiSheet, r++, dataStyle, "Total Asignadas", String.valueOf(report.getTotalAssigned()));
                createDataRow(kpiSheet, r++, dataStyle, "Completadas", String.valueOf(report.getTotalCompleted()));
                createDataRow(kpiSheet, r++, dataStyle, "Fallidas", String.valueOf(report.getTotalFailed()));
                createDataRow(kpiSheet, r, dataStyle, "Pendientes", String.valueOf(report.getTotalPending()));
            }
            kpiSheet.autoSizeColumn(0);
            kpiSheet.autoSizeColumn(1);

            // ── Sheet 2: Tareas ──
            Sheet tasksSheet = wb.createSheet("Tareas");
            String[] taskHeaders = {"ID", "Título", "Categoría", "Estado", "Turno", "A tiempo",
                    "Rework", "Inicio", "Fin", "Usuario Asignado"};
            createRow(tasksSheet, 0, taskHeaders, headerStyle);
            if (report.getTasks() != null) {
                int r = 1;
                for (TaskResponse t : report.getTasks()) {
                    createDataRow(tasksSheet, r++, dataStyle,
                            t.getId() != null ? t.getId() : "",
                            t.getTitle() != null ? t.getTitle() : "",
                            t.getCategory() != null ? t.getCategory().name() : "",
                            t.getStatus() != null ? t.getStatus().name() : "",
                            t.getShift() != null ? t.getShift() : "",
                            t.getOnTime() != null ? (t.getOnTime() ? "Sí" : "No") : "-",
                            String.valueOf(t.getReworkCount()),
                            t.getStartedAt() != null ? t.getStartedAt().toString() : "-",
                            t.getFinishedAt() != null ? t.getFinishedAt().toString() : "-",
                            t.getAssignedUserId() != null ? t.getAssignedUserId() : "");
                }
            }
            for (int i = 0; i < taskHeaders.length; i++) tasksSheet.autoSizeColumn(i);

            // ── Sheet 3: Colaboradores ──
            Sheet usersSheet = wb.createSheet("Colaboradores");
            String[] userHeaders = {"Rank", "Nombre", "Puesto", "Turno", "Total", "Completadas",
                    "Fallidas", "On-Time%", "Re-trabajo%", "Avg Ejec. (min)", "IGEO"};
            createRow(usersSheet, 0, userHeaders, headerStyle);
            if (report.getUserRanking() != null) {
                int r = 1;
                for (UserResponsibilityResponse u : report.getUserRanking()) {
                    createDataRow(usersSheet, r++, dataStyle,
                            String.valueOf(u.getRank()),
                            u.getNombre() != null ? u.getNombre() : "",
                            u.getPosition() != null ? u.getPosition() : "",
                            u.getTurno() != null ? u.getTurno() : "",
                            String.valueOf(u.getTotalTasks()),
                            String.valueOf(u.getCompletedTasks()),
                            String.valueOf(u.getFailedTasks()),
                            formatKpiPct(u.getOnTimeRate()),
                            formatKpiPct(u.getReworkRate()),
                            formatKpi(u.getAvgExecMinutes()),
                            formatKpi(u.getIgeo()));
                }
            }
            for (int i = 0; i < userHeaders.length; i++) usersSheet.autoSizeColumn(i);

            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel: " + e.getMessage(), e);
        }
    }

    // ── PDF helpers ───────────────────────────────────────────────────────

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(234, 88, 12));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void addKpiRow(PdfPTable table, String label, String value, Font font) {
        addCell(table, label, font);
        addCell(table, value, font);
    }

    // ── Excel helpers ──────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void createRow(Sheet sheet, int rowNum, String[] values, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private void createDataRow(Sheet sheet, int rowNum, CellStyle style, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i] != null ? values[i] : "");
            cell.setCellStyle(style);
        }
    }

    // ── Formatters ────────────────────────────────────────────────────────

    private String formatKpi(double value) {
        return value < 0 ? "S/D" : String.format("%.2f", value);
    }

    private String formatKpiPct(double value) {
        return value < 0 ? "S/D" : String.format("%.1f%%", value);
    }

    // ── Task → TaskResponse (minimal mapper) ─────────────────────────────

    private TaskResponse toTaskResponse(Task task) {
        var exec = task.getExecution();
        var evidence = exec.getEvidence();
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .category(task.getCategory())
                .status(exec.getStatus())
                .shift(task.getShift())
                .onTime(exec.getOnTime())
                .startedAt(exec.getStartedAt())
                .finishedAt(exec.getFinishedAt())
                .reworkCount(task.getReworkCount())
                .assignedUserId(task.getAssignedUserId())
                .position(task.getPosition())
                .storeId(task.getStoreId())
                .dueAt(task.getDueAt())
                .evidenceImages(evidence != null ? evidence.getImages() : List.of())
                .evidenceVideos(evidence != null ? evidence.getVideos() : List.of())
                .qualityRating(task.getQualityRating())
                .comments(task.getComments())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
