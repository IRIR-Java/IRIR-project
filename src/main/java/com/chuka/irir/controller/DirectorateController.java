package com.chuka.irir.controller;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.repository.ResearchProjectRepository;
import com.chuka.irir.service.AnalyticsService;
import com.chuka.irir.service.ReportExportService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Directorate of Research & Extension analytics dashboard.
 *
 * <p>Provides endpoints for viewing analytics, flagging projects for incubation,
 * and exporting reports as PDF or Excel. All endpoints require the
 * {@code ROLE_DIRECTORATE} authority (enforced via SecurityConfig URL rules).</p>
 */
@Controller
@RequestMapping("/directorate")
public class DirectorateController {

    private static final Logger log = LoggerFactory.getLogger(DirectorateController.class);

    private final AnalyticsService analyticsService;
    private final ReportExportService reportExportService;
    private final ResearchProjectRepository researchProjectRepository;
    private final JavaMailSender javaMailSender;

    public DirectorateController(AnalyticsService analyticsService,
                                 ReportExportService reportExportService,
                                 ResearchProjectRepository researchProjectRepository,
                                 JavaMailSender javaMailSender) {
        this.analyticsService = analyticsService;
        this.reportExportService = reportExportService;
        this.researchProjectRepository = researchProjectRepository;
        this.javaMailSender = javaMailSender;
    }

    // ==================== Dashboard ====================

    /**
     * Loads all analytics data and passes it to the Thymeleaf dashboard template.
     *
     * <p>Chart.js data is passed as Lists so Thymeleaf's {@code th:inline="javascript"}
     * can serialize them directly to JSON arrays. Model attribute names MUST match
     * the variable names referenced in the template's inlined script block.</p>
     *
     * @param model the Spring MVC model
     * @return view name for {@code templates/directorate/analytics-dashboard.html}
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // --- Chart data ---
        Map<String, Long> deptData   = analyticsService.getResearchByDepartment();
        Map<Integer, Long> trendsData = analyticsService.getResearchTrends(LocalDate.now().getYear());
        Map<String, Long> domainData = analyticsService.getTopResearchDomains(5);

        // Department bar chart
        model.addAttribute("departmentLabels", new ArrayList<>(deptData.keySet()));
        model.addAttribute("departmentCounts", new ArrayList<>(deptData.values()));

        // Monthly trends line chart — month labels are constant
        model.addAttribute("trendLabels",
                List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));
        model.addAttribute("trendCounts", buildTrendCounts(trendsData));

        // Research domains doughnut chart
        model.addAttribute("domainLabels", new ArrayList<>(domainData.keySet()));
        model.addAttribute("domainCounts", new ArrayList<>(domainData.values()));

        // --- Table data ---
        model.addAttribute("highPotentialProjects", analyticsService.getHighPotentialProjects());
        model.addAttribute("incubationProjects", analyticsService.getIncubationCandidates());

        // --- Summary statistics for stat cards ---
        model.addAttribute("summaryStats", analyticsService.getSummaryStats());

        // --- Current year for the trends chart heading ---
        model.addAttribute("currentYear", LocalDate.now().getYear());

        return "directorate/analytics-dashboard";
    }

    // ==================== Flag for Incubation ====================

    /**
     * Flags a research project for incubation and sends an email notification
     * to the project's student owner.
     *
     * @param id                  the project's primary key
     * @param redirectAttributes  flash attribute container for success/error messages
     * @return redirect to the dashboard
     */
    @PostMapping("/project/{id}/flag-incubation")
    public String flagIncubation(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        ResearchProject project = researchProjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found with ID: " + id));

        project.setIsIncubationFlagged(true);
        researchProjectRepository.save(project);

        // Send email notification to the student
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(project.getOwner().getEmail());
            helper.setSubject("🎉 Your project has been flagged for incubation!");
            helper.setText(
                    "Hello " + project.getOwner().getFullName() + ",\n\n"
                  + "Great news! Your project '" + project.getTitle()
                  + "' has been flagged for incubation by the Directorate "
                  + "of Research & Extension.\n\n"
                  + "This means your work has been identified as having high "
                  + "innovation potential. You will be contacted with next "
                  + "steps soon.\n\n"
                  + "Best regards,\nIRIR — Chuka University",
                    false);
            javaMailSender.send(message);
        } catch (MessagingException ex) {
            log.warn("Failed to send incubation notification for project {}: {}",
                    id, ex.getMessage());
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Project \"" + project.getTitle() + "\" flagged for incubation successfully.");
        return "redirect:/directorate/dashboard";
    }

    // ==================== PDF Export ====================

    /**
     * Generates and downloads a formatted PDF analytics report.
     * The report contains department distribution, monthly trends,
     * top research domains, and high-potential project listings.
     *
     * @return PDF file as a downloadable response
     * @throws IOException if PDF generation fails
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf() throws IOException {
        // Build complete analytics data map for the PDF report
        Map<String, Object> analyticsData = new HashMap<>();
        analyticsData.put("departmentData", analyticsService.getResearchByDepartment());
        analyticsData.put("trendsData", analyticsService.getResearchTrends(LocalDate.now().getYear()));
        analyticsData.put("domainData", analyticsService.getTopResearchDomains(5));
        analyticsData.put("highPotentialProjects", analyticsService.getHighPotentialProjects());
        analyticsData.put("summaryStats", analyticsService.getSummaryStats());

        byte[] pdfBytes = reportExportService.exportToPDF(analyticsData);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=irir-analytics-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== Excel Export ====================

    /**
     * Generates and downloads an Excel spreadsheet of all research projects
     * with full metadata (title, department, keywords, scores, dates, etc.).
     *
     * @return XLSX file as a downloadable response
     * @throws IOException if Excel generation fails
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        List<ResearchProject> allProjects = analyticsService.getAllProjects();
        byte[] excelBytes = reportExportService.exportToExcel(allProjects);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=irir-research-projects.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // ==================== Helper Methods ====================

    /**
     * Converts the month→count map into an ordered List of 12 values
     * suitable for direct serialization into a Chart.js data array.
     */
    private List<Long> buildTrendCounts(Map<Integer, Long> trends) {
        List<Long> counts = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            counts.add(trends.getOrDefault(month, 0L));
        }
        return counts;
    }
}
