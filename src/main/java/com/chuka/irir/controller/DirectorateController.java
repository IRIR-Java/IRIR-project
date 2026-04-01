package com.chuka.irir.controller;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.service.AnalyticsService;
import com.chuka.irir.service.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/directorate")
@PreAuthorize("hasRole('DIRECTORATE')")
public class DirectorateController {

    private final AnalyticsService analyticsService;
    private final ReportExportService reportExportService;

    public DirectorateController(AnalyticsService analyticsService, ReportExportService reportExportService) {
        this.analyticsService = analyticsService;
        this.reportExportService = reportExportService;
    }

    /**
     * Loads all analytics data and passes to template.
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Department data
        Map<String, Long> departmentData = analyticsService.getResearchByDepartment();
        model.addAttribute("departmentLabels", departmentData.keySet());
        model.addAttribute("departmentCounts", departmentData.values());

        // Trends data (last 12 months)
        int currentYear = java.time.LocalDate.now().getYear();
        Map<Integer, Long> trendsData = analyticsService.getResearchTrends(currentYear);
        model.addAttribute("trendsLabels", trendsData.keySet().stream().map(m -> "Month " + m).collect(java.util.stream.Collectors.toList()));
        model.addAttribute("trendsCounts", trendsData.values());

        // Top domains
        List<Map.Entry<String, Long>> topDomains = analyticsService.getTopResearchDomains(10);
        model.addAttribute("domainLabels", topDomains.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toList()));
        model.addAttribute("domainCounts", topDomains.stream().map(Map.Entry::getValue).collect(java.util.stream.Collectors.toList()));

        // High potential projects
        List<ResearchProject> highPotential = analyticsService.getHighPotentialProjects();
        model.addAttribute("highPotentialProjects", highPotential);

        // Incubation candidates
        List<ResearchProject> incubationCandidates = analyticsService.getIncubationCandidates();
        model.addAttribute("incubationCandidates", incubationCandidates);

        return "analytics-dashboard";
    }

    /**
     * Flags project for incubation and notifies student.
     */
    @PostMapping("/project/{id}/flag-incubation")
    public String flagIncubation(@PathVariable Long id) {
        // TODO: Implement flagging logic and notification
        // For now, just redirect back
        return "redirect:/directorate/dashboard";
    }

    /**
     * Generates and downloads analytics report as PDF.
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf() throws IOException {
        // Create analytics data object
        AnalyticsData data = new AnalyticsData(
            analyticsService.getResearchByDepartment(),
            analyticsService.getResearchTrends(java.time.LocalDate.now().getYear()),
            analyticsService.getTopResearchDomains(10),
            analyticsService.getHighPotentialProjects(),
            analyticsService.getIncubationCandidates()
        );

        byte[] pdfBytes = reportExportService.exportToPDF(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=analytics-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Generates Excel report of all projects with metadata.
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        List<ResearchProject> allProjects = analyticsService.getAllProjects();
        byte[] excelBytes = reportExportService.exportToExcel(allProjects);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=projects-report.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    // Inner class for analytics data
    public static class AnalyticsData {
        public Map<String, Long> departmentData;
        public Map<Integer, Long> trendsData;
        public List<Map.Entry<String, Long>> topDomains;
        public List<ResearchProject> highPotential;
        public List<ResearchProject> incubationCandidates;

        public AnalyticsData(Map<String, Long> departmentData, Map<Integer, Long> trendsData, List<Map.Entry<String, Long>> topDomains, List<ResearchProject> highPotential, List<ResearchProject> incubationCandidates) {
            this.departmentData = departmentData;
            this.trendsData = trendsData;
            this.topDomains = topDomains;
            this.highPotential = highPotential;
            this.incubationCandidates = incubationCandidates;
        }
    }
}