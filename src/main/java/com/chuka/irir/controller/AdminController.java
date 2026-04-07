package com.chuka.irir.controller;

import com.chuka.irir.model.*;
import com.chuka.irir.repository.*;
import com.chuka.irir.service.AiInsightService;
import com.chuka.irir.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ReviewRepository reviewRepository;
    private final ProjectScoreRepository projectScoreRepository;
    private final ProjectInsightRepository projectInsightRepository;
    private final SimilarityReportRepository similarityReportRepository;
    private final AuditLogRepository auditLogRepository;
    private final AiInsightService aiInsightService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository,
                           ProjectRepository projectRepository,
                           ReviewRepository reviewRepository,
                           ProjectScoreRepository projectScoreRepository,
                           ProjectInsightRepository projectInsightRepository,
                           SimilarityReportRepository similarityReportRepository,
                           AuditLogRepository auditLogRepository,
                           AiInsightService aiInsightService,
                           NotificationService notificationService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.reviewRepository = reviewRepository;
        this.projectScoreRepository = projectScoreRepository;
        this.projectInsightRepository = projectInsightRepository;
        this.similarityReportRepository = similarityReportRepository;
        this.auditLogRepository = auditLogRepository;
        this.aiInsightService = aiInsightService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    // ──────────────────────── Dashboard ────────────────────────

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.count();
        long pendingReview = projectRepository.countByStatus(ProjectStatus.SUBMITTED)
                           + projectRepository.countByStatus(ProjectStatus.UNDER_REVIEW);
        long approved = projectRepository.countByStatus(ProjectStatus.APPROVED);
        long rejected = projectRepository.countByStatus(ProjectStatus.REJECTED);
        long flagged = projectRepository.countByStatus(ProjectStatus.FLAGGED);
        long thisMonth = projectRepository.countProjectsSince(
                LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0));

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) {
            statusCounts.put(s.name(), projectRepository.countByStatus(s));
        }

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalProjects", totalProjects);
        model.addAttribute("pendingReview", pendingReview);
        model.addAttribute("approved", approved);
        model.addAttribute("rejected", rejected);
        model.addAttribute("flagged", flagged);
        model.addAttribute("projectsThisMonth", thisMonth);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("pageTitle", "Admin Dashboard");
        return "dashboard/admin";
    }

    // ──────────────────────── Projects List ────────────────────────

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    public String projects(@RequestParam(name = "status", required = false) String statusStr,
                           @RequestParam(name = "q", required = false) String query,
                           Model model) {
        ProjectStatus filterStatus = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try { filterStatus = ProjectStatus.valueOf(statusStr); } catch (IllegalArgumentException ignored) {}
        }
        String searchTerm = (query != null && !query.isBlank()) ? query.trim() : null;

        List<Project> projects;
        if (filterStatus != null && searchTerm != null) {
            projects = projectRepository.findByStatusAndSearchTerm(filterStatus, searchTerm);
        } else if (filterStatus != null) {
            projects = projectRepository.findByStatusOrderBySubmittedAtDesc(filterStatus);
        } else if (searchTerm != null) {
            projects = projectRepository.findBySearchTerm(searchTerm);
        } else {
            projects = projectRepository.findAllByOrderByCreatedAtDesc();
        }

        // Force-initialize lazy submittedBy to avoid LazyInitializationException in template
        for (Project p : projects) {
            if (p.getSubmittedBy() != null) {
                p.getSubmittedBy().getFirstName();
            }
        }

        long total = projectRepository.count();
        long pending = projectRepository.countByStatus(ProjectStatus.SUBMITTED);
        long approvedCount = projectRepository.countByStatus(ProjectStatus.APPROVED);
        long flaggedCount = projectRepository.countByStatus(ProjectStatus.FLAGGED);

        model.addAttribute("projects", projects);
        model.addAttribute("statuses", ProjectStatus.values());
        model.addAttribute("selectedStatus", statusStr);
        model.addAttribute("query", query);
        model.addAttribute("totalCount", total);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("flaggedCount", flaggedCount);
        model.addAttribute("pageTitle", "All Projects");
        return "admin/projects";
    }

    // ──────────────────────── Project Detail ────────────────────────

    @GetMapping("/projects/{id}")
    @Transactional(readOnly = true)
    public String projectDetail(@PathVariable("id") Long id, Model model,
                                RedirectAttributes redirectAttributes) {
        Optional<Project> opt = projectRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Project not found.");
            return "redirect:/admin/projects";
        }
        Project project = opt.get();
        // Initialize lazy associations to avoid LazyInitializationException in template
        if (project.getFiles() != null) project.getFiles().size();
        if (project.getKeywords() != null) project.getKeywords().size();
        if (project.getSubmittedBy() != null) project.getSubmittedBy().getFirstName();
        if (project.getSupervisor() != null) project.getSupervisor().getFirstName();

        List<Review> reviews = reviewRepository.findByProjectIdOrderByReviewedAtDesc(id);
        // Initialize lazy reviewer on each review
        for (Review r : reviews) {
            if (r.getReviewer() != null) r.getReviewer().getFirstName();
        }

        List<ProjectScore> scores = projectScoreRepository.findByProjectId(id);
        // Initialize lazy scorer on each score
        for (ProjectScore s : scores) {
            if (s.getScorer() != null) s.getScorer().getFirstName();
        }

        Double avgScore = projectScoreRepository.findAverageScoreByProjectId(id);
        Optional<ProjectInsight> insight = projectInsightRepository.findByProjectId(id);
        List<SimilarityReport> simReports = similarityReportRepository.findBySourceProjectIdWithDetails(id);
        Double maxSim = similarityReportRepository.findMaxSimilarityScoreByProjectId(id);

        model.addAttribute("project", project);
        model.addAttribute("reviews", reviews);
        model.addAttribute("scores", scores);
        model.addAttribute("avgScore", avgScore);
        model.addAttribute("insight", insight.orElse(null));
        model.addAttribute("similarityReports", simReports);
        model.addAttribute("maxSimilarity", maxSim);
        model.addAttribute("statusOptions", ProjectStatus.values());
        model.addAttribute("pageTitle", "Project: " + project.getTitle());
        return "admin/project-detail";
    }

    // ──────────────────────── Score a Project ────────────────────────

    @PostMapping("/projects/{id}/score")
    public String scoreProject(@PathVariable("id") Long id,
                               @RequestParam("originality") Integer originality,
                               @RequestParam("methodology") Integer methodology,
                               @RequestParam("presentation") Integer presentation,
                               @RequestParam("impact") Integer impact,
                               @RequestParam(value = "comments", required = false) String comments,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("Project", "id", id));
        User scorer = getUser(authentication);

        // Update existing score or create new
        ProjectScore score = projectScoreRepository.findByProjectAndScorer(project, scorer)
                .orElse(ProjectScore.builder().project(project).scorer(scorer).build());

        score.setOriginality(clamp(originality));
        score.setMethodology(clamp(methodology));
        score.setPresentation(clamp(presentation));
        score.setImpact(clamp(impact));
        score.setComments(comments);
        projectScoreRepository.save(score);

        redirectAttributes.addFlashAttribute("successMessage",
                "Score saved. Overall: " + String.format("%.1f", score.getOverallScore()));
        return "redirect:/admin/projects/" + id;
    }

    // ──────────────────────── AI Review ────────────────────────

    @PostMapping("/projects/{id}/ai-review")
    public String triggerAiReview(@PathVariable("id") Long id,
                                  RedirectAttributes redirectAttributes) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("Project", "id", id));
        try {
            aiInsightService.generateDetailedReview(project);
            redirectAttributes.addFlashAttribute("successMessage", "AI analysis completed successfully.");
        } catch (Exception e) {
            logger.error("AI review failed for project {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "AI analysis failed: " + e.getMessage());
        }
        return "redirect:/admin/projects/" + id;
    }

    // ──────────────────────── Change Status ────────────────────────

    @PostMapping("/projects/{id}/status")
    public String changeStatus(@PathVariable("id") Long id,
                               @RequestParam("status") String statusStr,
                               @RequestParam(value = "reason", required = false) String reason,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("Project", "id", id));
        User admin = getUser(authentication);

        try {
            ProjectStatus newStatus = ProjectStatus.valueOf(statusStr);
            ProjectStatus oldStatus = project.getStatus();
            project.setStatus(newStatus);
            projectRepository.save(project);

            // Create a review record for the status change
            Review review = Review.builder()
                    .project(project)
                    .reviewer(admin)
                    .decision(mapStatusToDecision(newStatus))
                    .comments("[Admin Status Change] " + oldStatus + " → " + newStatus +
                              (reason != null ? ". Reason: " + reason : ""))
                    .build();
            reviewRepository.save(review);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Status changed to " + newStatus.name());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid status.");
        }
        return "redirect:/admin/projects/" + id;
    }

    // ──────────────────────── Admin Feedback ────────────────────────

    @PostMapping("/projects/{id}/feedback")
    public String addFeedback(@PathVariable("id") Long id,
                              @RequestParam("comments") String comments,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("Project", "id", id));
        User admin = getUser(authentication);

        Review feedback = Review.builder()
                .project(project)
                .reviewer(admin)
                .decision(ReviewDecision.REVISION_REQUESTED)
                .comments(comments)
                .build();
        reviewRepository.save(feedback);

        redirectAttributes.addFlashAttribute("successMessage", "Feedback submitted.");
        return "redirect:/admin/projects/" + id;
    }

    // ──────────────────────── Analytics ────────────────────────

    @GetMapping("/analytics")
    public String analytics(Model model) {
        long totalProjects = projectRepository.count();
        long totalUsers = userRepository.count();
        long totalStudents = userRepository.findByRole(Role.STUDENT).size();
        long approved = projectRepository.countByStatus(ProjectStatus.APPROVED);
        long total = projectRepository.countSubmittedProjects();
        double approvalRate = total > 0 ? (double) approved / total * 100 : 0;
        long thisMonth = projectRepository.countProjectsSince(
                LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0));

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) {
            statusCounts.put(s.name(), projectRepository.countByStatus(s));
        }

        model.addAttribute("totalProjects", totalProjects);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("approvalRate", String.format("%.1f", approvalRate));
        model.addAttribute("projectsThisMonth", thisMonth);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("statusLabels", new ArrayList<>(statusCounts.keySet()));
        model.addAttribute("statusValues", new ArrayList<>(statusCounts.values()));
        model.addAttribute("pageTitle", "Analytics");
        return "admin/analytics";
    }

    // ──────────────────────── Existing Endpoints ────────────────────────

    @GetMapping("/users")
    public String users(@RequestParam(name = "q", required = false) String query, Model model) {
        List<User> users = (query != null && !query.isBlank())
                ? userRepository.searchByNameOrEmail(query)
                : userRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("query", query);
        model.addAttribute("pageTitle", "User Management");
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String createUser(Model model) {
        model.addAttribute("pageTitle", "Create User");
        return "admin/users-create";
    }

    @PostMapping("/users/create")
    public String createUserSubmit(@RequestParam("firstName") String firstName,
                                   @RequestParam("lastName") String lastName,
                                   @RequestParam("email") String email,
                                   @RequestParam(value = "studentId", required = false) String studentId,
                                   @RequestParam("role") String role,
                                   @RequestParam(value = "department", required = false) String department,
                                   @RequestParam("password") String password,
                                   RedirectAttributes redirectAttributes) {
        if (firstName == null || firstName.isBlank()
                || lastName == null || lastName.isBlank()
                || email == null || email.isBlank()
                || role == null || role.isBlank()
                || password == null || password.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please fill in all required fields.");
            return "redirect:/admin/users/create";
        }

        if (userRepository.existsByEmail(email.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "A user with this email already exists.");
            return "redirect:/admin/users/create";
        }

        if (studentId != null && !studentId.isBlank() && userRepository.existsByStudentId(studentId.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "A user with this student ID already exists.");
            return "redirect:/admin/users/create";
        }

        Role resolvedRole;
        try {
            resolvedRole = Role.valueOf(role);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid role selected.");
            return "redirect:/admin/users/create";
        }

        User user = User.builder()
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .email(email.trim())
                .studentId(studentId != null ? studentId.trim() : null)
                .department((department != null && !department.isBlank()) ? department.trim() : "Computer Science")
                .role(resolvedRole)
                .password(passwordEncoder.encode(password))
                .enabled(true)
                .accountNonLocked(true)
                .build();

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "User created successfully.");
        return "redirect:/admin/users";
    }

    @GetMapping("/logs")
    @Transactional(readOnly = true)
    public String logs(@RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        int safePage = Math.max(0, page);
        Page<AuditLog> logPage = auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(safePage, 50));
        for (AuditLog log : logPage.getContent()) {
            if (log.getUser() != null) {
                log.getUser().getFirstName();
            }
        }
        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("page", safePage);
        model.addAttribute("hasNext", logPage.hasNext());
        model.addAttribute("hasPrev", logPage.hasPrevious());
        model.addAttribute("pageTitle", "System Logs");
        return "admin/logs";
    }

    @GetMapping("/backup")
    public String backup(Model model) {
        model.addAttribute("pageTitle", "Backups");
        return "admin/backup";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "System Settings");
        return "admin/settings";
    }

    // ──────────────────────── Helpers ────────────────────────

    private User getUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("User", "email", email));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private ReviewDecision mapStatusToDecision(ProjectStatus status) {
        return switch (status) {
            case APPROVED -> ReviewDecision.APPROVED;
            case REJECTED -> ReviewDecision.REJECTED;
            default -> ReviewDecision.REVISION_REQUESTED;
        };
    }
}
