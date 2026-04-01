package com.chuka.irir.service;

import com.chuka.irir.controller.DirectorateController;
import com.chuka.irir.model.ResearchProject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ReportExportService {

    /**
     * Exports analytics data to PDF.
     */
    public byte[] exportToPDF(DirectorateController.AnalyticsData data) throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 750);
        contentStream.showText("Research Analytics Report");
        contentStream.endText();

        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 700);
        contentStream.showText("Department Data:");
        contentStream.newLineAtOffset(0, -20);
        for (Map.Entry<String, Long> entry : data.departmentData.entrySet()) {
            contentStream.showText(entry.getKey() + ": " + entry.getValue());
            contentStream.newLineAtOffset(0, -15);
        }
        contentStream.endText();

        // Add more sections as needed

        contentStream.close();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.save(outputStream);
        document.close();
        return outputStream.toByteArray();
    }

    /**
     * Exports list of projects to Excel.
     */
    public byte[] exportToExcel(List<ResearchProject> projects) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Projects");

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "Title", "Department", "Supervisor", "Upload Date", "Status", "Similarity Score", "View Count", "Incubation Flagged"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // Data rows
        int rowNum = 1;
        for (ResearchProject project : projects) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(project.getProjectId());
            row.createCell(1).setCellValue(project.getTitle());
            row.createCell(2).setCellValue(project.getDepartment());
            row.createCell(3).setCellValue(project.getSupervisorName());
            row.createCell(4).setCellValue(project.getUploadDate() != null ? project.getUploadDate().toString() : "");
            row.createCell(5).setCellValue(project.getStatus().toString());
            row.createCell(6).setCellValue(project.getSimilarityScore() != null ? project.getSimilarityScore() : 0);
            row.createCell(7).setCellValue(project.getViewCount() != null ? project.getViewCount() : 0);
            row.createCell(8).setCellValue(Boolean.TRUE.equals(project.getIsIncubationFlagged()));
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        return outputStream.toByteArray();
    }
}