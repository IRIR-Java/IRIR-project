package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.repository.ResearchProjectRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for exporting analytics data as PDF and Excel files.
 *
 * <ul>
 *   <li><strong>PDF</strong>: Uses iText 7 to generate a formatted multi-section
 *       analytics report with tables for department distribution, monthly trends,
 *       top research domains, and high-potential projects.</li>
 *   <li><strong>Excel</strong>: Uses Apache POI to generate a styled .xlsx workbook
 *       with bold headers, frozen header row, and auto-sized columns.</li>
 * </ul>
 */
@Service
public class ReportExportService {

    private final ResearchProjectRepository researchProjectRepository;

    public ReportExportService(ResearchProjectRepository researchProjectRepository) {
        this.researchProjectRepository = researchProjectRepository;
    }

    // ==================== PDF Export ====================

    /**
     * Generates a formatted PDF analytics report from the provided data map.
     *
     * <p>Expected keys in {@code analyticsData}:</p>
     * <ul>
     *   <li>{@code departmentData} — {@code Map<String, Long>}</li>
     *   <li>{@code trendsData} — {@code Map<Integer, Long>}</li>
     *   <li>{@code domainData} — {@code Map<String, Long>}</li>
     *   <li>{@code highPotentialProjects} — {@code List<ResearchProject>}</li>
     *   <li>{@code summaryStats} — {@code Map<String, Long>}</li>
     * </ul>
     *
     * @param analyticsData map of analytics data keyed by section name
     * @return PDF file contents as a byte array
     * @throws IOException if PDF generation fails
     */
    @SuppressWarnings("unchecked")
    public byte[] exportToPDF(Map<String, Object> analyticsData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        // ---- Title ----
        document.add(new Paragraph("IRIR — Research Analytics Report")
                .setBold().setFontSize(20).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated: " + LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
                .setFontSize(10).setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));

        // ---- Summary Statistics ----
        Map<String, Long> stats = (Map<String, Long>) analyticsData.get("summaryStats");
        if (stats != null) {
            document.add(createSectionHeading("Summary Statistics"));
            Table statsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                    .useAllAvailableWidth();
            addHeaderRow(statsTable, "Metric", "Count");
            statsTable.addCell(createCell("Total Projects"));
            statsTable.addCell(createCell(String.valueOf(stats.getOrDefault("totalProjects", 0L))));
            statsTable.addCell(createCell("Approved Projects"));
            statsTable.addCell(createCell(String.valueOf(stats.getOrDefault("totalApproved", 0L))));
            statsTable.addCell(createCell("Pending Review"));
            statsTable.addCell(createCell(String.valueOf(stats.getOrDefault("totalPending", 0L))));
            statsTable.addCell(createCell("Incubation Candidates"));
            statsTable.addCell(createCell(String.valueOf(stats.getOrDefault("totalIncubation", 0L))));
            document.add(statsTable);
            document.add(new Paragraph(" "));
        }

        // ---- Department Distribution ----
        Map<String, Long> departmentData = (Map<String, Long>) analyticsData.get("departmentData");
        if (departmentData != null && !departmentData.isEmpty()) {
            document.add(createSectionHeading("Projects per Department"));
            Table deptTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                    .useAllAvailableWidth();
            addHeaderRow(deptTable, "Department", "Count");
            for (Map.Entry<String, Long> entry : departmentData.entrySet()) {
                deptTable.addCell(createCell(entry.getKey()));
                deptTable.addCell(createCell(String.valueOf(entry.getValue())));
            }
            document.add(deptTable);
            document.add(new Paragraph(" "));
        }

        // ---- Monthly Trends ----
        Map<Integer, Long> trendsData = (Map<Integer, Long>) analyticsData.get("trendsData");
        if (trendsData != null && !trendsData.isEmpty()) {
            document.add(createSectionHeading("Monthly Submission Trends (" + LocalDate.now().getYear() + ")"));
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            Table trendsTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                    .useAllAvailableWidth();
            addHeaderRow(trendsTable, "Month", "Submissions");
            for (int m = 1; m <= 12; m++) {
                trendsTable.addCell(createCell(months[m - 1]));
                trendsTable.addCell(createCell(String.valueOf(trendsData.getOrDefault(m, 0L))));
            }
            document.add(trendsTable);
            document.add(new Paragraph(" "));
        }

        // ---- Top Research Domains ----
        Map<String, Long> domainData = (Map<String, Long>) analyticsData.get("domainData");
        if (domainData != null && !domainData.isEmpty()) {
            document.add(createSectionHeading("Top Research Domains"));
            Table domainTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                    .useAllAvailableWidth();
            addHeaderRow(domainTable, "Domain (Keyword)", "Frequency");
            for (Map.Entry<String, Long> entry : domainData.entrySet()) {
                domainTable.addCell(createCell(entry.getKey()));
                domainTable.addCell(createCell(String.valueOf(entry.getValue())));
            }
            document.add(domainTable);
            document.add(new Paragraph(" "));
        }

        // ---- High-Potential Projects ----
        List<ResearchProject> highPotential =
                (List<ResearchProject>) analyticsData.get("highPotentialProjects");
        if (highPotential != null && !highPotential.isEmpty()) {
            document.add(createSectionHeading("High-Potential Projects"));
            Table hpTable = new Table(UnitValue.createPercentArray(new float[]{3, 2, 1, 1}))
                    .useAllAvailableWidth();
            addHeaderRow(hpTable, "Title", "Student", "Similarity %", "Views");
            for (ResearchProject p : highPotential) {
                hpTable.addCell(createCell(p.getTitle()));
                hpTable.addCell(createCell(
                        p.getOwner() != null ? p.getOwner().getFullName() : "N/A"));
                hpTable.addCell(createCell(
                        p.getSimilarityScore() != null
                                ? String.format("%.1f%%", p.getSimilarityScore() * 100) : "N/A"));
                hpTable.addCell(createCell(
                        p.getViewCount() != null ? String.valueOf(p.getViewCount()) : "0"));
            }
            document.add(hpTable);
        }

        document.close();
        return baos.toByteArray();
    }

    // ==================== Excel Export ====================

    /**
     * Generates a styled Excel (.xlsx) workbook containing all research projects
     * with full metadata columns.
     *
     * <p>Features: bold header row, frozen top row, auto-sized columns
     * (up to 200 rows — fixed widths for larger datasets).</p>
     *
     * @param projects list of research projects to export
     * @return XLSX file contents as a byte array
     * @throws IOException if workbook generation fails
     */
    public byte[] exportToExcel(List<ResearchProject> projects) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Research Projects");

            // ---- Header style: bold, white on dark teal background ----
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // ---- Date style ----
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

            // ---- Header row ----
            String[] headers = {
                    "ID", "Title", "Department", "Keywords",
                    "Similarity Score", "View Count", "Download Count",
                    "Incubation Flagged", "Status", "Submission Date", "Student Name"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Freeze the header row for easy scrolling
            sheet.createFreezePane(0, 1);

            // ---- Data rows ----
            int rowNum = 1;
            for (ResearchProject project : projects) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(
                        project.getProjectId() != null ? project.getProjectId() : 0);
                row.createCell(1).setCellValue(
                        project.getTitle() != null ? project.getTitle() : "");
                row.createCell(2).setCellValue(
                        project.getDepartment() != null ? project.getDepartment() : "");
                row.createCell(3).setCellValue(
                        project.getKeywords() != null ? project.getKeywords() : "");
                row.createCell(4).setCellValue(
                        project.getSimilarityScore() != null ? project.getSimilarityScore() : 0.0);
                row.createCell(5).setCellValue(
                        project.getViewCount() != null ? project.getViewCount() : 0);
                row.createCell(6).setCellValue(
                        project.getDownloadCount() != null ? project.getDownloadCount() : 0);
                row.createCell(7).setCellValue(
                        Boolean.TRUE.equals(project.getIsIncubationFlagged()) ? "Yes" : "No");
                row.createCell(8).setCellValue(
                        project.getStatus() != null ? project.getStatus().name() : "");

                org.apache.poi.ss.usermodel.Cell dateCell = row.createCell(9);
                if (project.getUploadDate() != null) {
                    dateCell.setCellValue(project.getUploadDate().toString());
                } else {
                    dateCell.setCellValue("");
                }

                row.createCell(10).setCellValue(
                        project.getOwner() != null ? project.getOwner().getFullName() : "");
            }

            // ---- Auto-size columns (efficient for ≤ 200 rows; fixed widths otherwise) ----
            if (projects.size() <= 200) {
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            } else {
                int[] defaultWidths = {2000, 10000, 5000, 8000, 4000, 3000, 3000, 4000, 3000, 5000, 6000};
                for (int i = 0; i < headers.length; i++) {
                    sheet.setColumnWidth(i, defaultWidths[i]);
                }
            }

            // ---- Auto-filter on entire header row ----
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    // ==================== PDF Helper Methods ====================

    /** Creates a bold section heading paragraph for the PDF. */
    private Paragraph createSectionHeading(String text) {
        return new Paragraph(text)
                .setBold()
                .setFontSize(14)
                .setMarginTop(10)
                .setMarginBottom(5);
    }

    /** Adds a styled header row to an iText table. */
    private void addHeaderRow(Table table, String... headers) {
        for (String header : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(ColorConstants.DARK_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
        }
    }

    /** Creates a standard body cell for an iText table. */
    private Cell createCell(String text) {
        return new Cell().add(new Paragraph(text != null ? text : ""));
    }
}
