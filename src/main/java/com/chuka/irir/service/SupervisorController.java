package com.chuka.irir.service;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.Review;
import com.chuka.irir.model.ReviewDecision;
import com.chuka.irir.model.SimilarityReport;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.ReviewRepository;
import com.chuka.irir.repository.SimilarityReportRepository;
import com.chuka.irir.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

    private final ProjectRepository projectRepository;
    private final ReviewRepository reviewRepository;
    private final SimilarityReportRepository similarityReportRepository;
    private final SimilarityDetectionService similarityDetectionService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public SupervisorController(ProjectRepository projectRepository,
                                ReviewRepository reviewRepository,
                                SimilarityReportRepository similarityReportRepository,
                                SimilarityDetectionService similarityDetectionService,
                                NotificationService notificationService,
                                UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.reviewRepository = reviewRepository;
        this.similarityReportRepository = similarityReportRepository;
        this.similarityDetectionService = similarityDetectionService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/supervisor/reviews";
    }

    @GetMapping("/reviews")
    public String reviews(Authentication authentication, Model model) {
        User user = getUser(authentication);
        List<Project> assigned = projectRepository.findBySupervisorOrderBySubmittedAtDesc(user);
        List<Project> pendingReview = projectRepository.findByStatusOrderBySubmittedAtDesc(ProjectStatus.SUBMITTED);

        model.addAttribute("user", user);
        model.addAttribute("assignedProjects", assigned);
        model.addAttribute("pendingReview", pendingReview);
        model.addAttribute("pageTitle", "Supervisor Reviews");
        return "supervisor/reviews";
    }

    @GetMapping("/review/{id}")
    public String reviewDetail(@PathVariable("id") Long projectId,
                               Authentication authentication,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("Project", "id", projectId));

        if (project.getSupervisor() != null && !project.getSupervisor().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not assigned to this project.");
            return "redirect:/supervisor/reviews";
        }
        if (project.getFiles() != null) {
            project.getFiles().size();
        }

        List<SimilarityReport> similarityReports =
                similarityReportRepository.findBySourceProjectIdWithDetails(project.getId());
        Double maxSimilarityScore = similarityReportRepository.findMaxSimilarityScoreByProjectId(project.getId());
        String verdictLabel = maxSimilarityScore != null
                ? similarityDetectionService.classifyScore(maxSimilarityScore)
                : "Original Work";
        List<Review> reviews = reviewRepository.findByProjectIdOrderByReviewedAtDesc(project.getId());

        model.addAttribute("user", user);
        model.addAttribute("project", project);
        model.addAttribute("similarityReports", similarityReports);
        model.addAttribute("maxSimilarityScore", maxSimilarityScore);
        model.addAttribute("verdictLabel", verdictLabel);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewDecisionOptions", ReviewDecision.values());
        model.addAttribute("pageTitle", "Review Project");
        return "supervisor/review";
    }

    @PostMapping("/review/{id}")
    public String submitReview(@PathVariable("id") Long projectId,
                               @RequestParam("decision") ReviewDecision decision,
                               @RequestParam(value = "comments", required = false) String comments,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        User reviewer = getUser(authentication);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("Project", "id", projectId));

        if (project.getSupervisor() != null && !project.getSupervisor().getId().equals(reviewer.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not assigned to this project.");
            return "redirect:/supervisor/reviews";
        }

        Review review = Review.builder()
                .project(project)
                .reviewer(reviewer)
                .decision(decision)
                .comments(comments)
                .build();
        reviewRepository.save(review);

        if (decision == ReviewDecision.APPROVED) {
            project.setStatus(ProjectStatus.APPROVED);
            notificationService.sendApprovalNotification(project.getSubmittedBy(), project);
        } else if (decision == ReviewDecision.REJECTED) {
            project.setStatus(ProjectStatus.REJECTED);
            notificationService.sendRejectionNotification(project.getSubmittedBy(), project,
                    comments != null && !comments.isBlank() ? comments : "No feedback provided.");
        } else {
            project.setStatus(ProjectStatus.UNDER_REVIEW);
        }
        projectRepository.save(project);

        redirectAttributes.addFlashAttribute("successMessage", "Review submitted successfully.");
        return "redirect:/supervisor/review/" + projectId;
    }

    private User getUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new com.chuka.irir.exception.ResourceNotFoundException("User", "email", email));
    }
}
