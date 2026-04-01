package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.repository.ResearchProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

    @Autowired
    private ResearchProjectRepository projectRepository;

    @Autowired
    private FeedbackService feedbackService;

    // GET /supervisor/dashboard → Lists all projects grouped by status
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<ResearchProject> allProjects = projectRepository.findAll();

        Map<String, List<ResearchProject>> groupedProjects = allProjects.stream()
            .collect(Collectors.groupingBy(p -> p.getStatus().name()));

        model.addAttribute("pendingProjects", groupedProjects.getOrDefault("PENDING", List.of()));
        model.addAttribute("approvedProjects", groupedProjects.getOrDefault("APPROVED", List.of()));
        model.addAttribute("rejectedProjects", groupedProjects.getOrDefault("REJECTED", List.of()));

        return "supervisor-dashboard";
    }

    // GET /supervisor/project/{id} → Shows full project detail
    @GetMapping("/project/{id}")
    public String viewProjectDetail(@PathVariable Long id, Model model) {
        ResearchProject project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        model.addAttribute("project", project);
        model.addAttribute("feedbackDTO", new FeedbackDTO());
        return "project-detail";
    }

    // POST /supervisor/project/{id}/review → Receives FeedbackDTO, updates project status
    @PostMapping("/project/{id}/review")
    public String reviewProject(@PathVariable Long id,
                                @ModelAttribute FeedbackDTO dto,
                                @SessionAttribute(value = "userId", required = false) Long supervisorId,
                                RedirectAttributes redirectAttributes) {
        if (supervisorId != null) {
            feedbackService.submitFeedback(id, dto, supervisorId);
        }
        redirectAttributes.addFlashAttribute("message", "Feedback submitted successfully.");
        return "redirect:/supervisor/dashboard";
    }

    // GET /supervisor/projects/search → Filter projects by keyword
    @GetMapping("/projects/search")
    public String searchProjects(
            @RequestParam(required = false) String keyword,
            Model model) {

        List<ResearchProject> filteredProjects;
        if (keyword != null && !keyword.isBlank()) {
            filteredProjects = projectRepository.searchByTitleOrKeyword(keyword);
        } else {
            filteredProjects = projectRepository.findAll();
        }
        model.addAttribute("projects", filteredProjects);
        return "supervisor-search-results";
    }
}
