package com.irir.controller;

import com.irir.dto.FeedbackDTO;
import com.irir.model.ResearchProject;
import com.irir.service.FeedbackService;
import com.irir.service.ResearchProjectService;
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
    private ResearchProjectService projectService;
    
    @Autowired
    private FeedbackService feedbackService;

    // GET /supervisor/dashboard → Lists all projects assigned to this supervisor, grouped by status (PENDING, APPROVED, REJECTED)
    @GetMapping("/dashboard")
    public String dashboard(Model model, @SessionAttribute("userId") Long supervisorId) {
        List<ResearchProject> assignedProjects = projectService.getProjectsBySupervisor(supervisorId);
        
        Map<String, List<ResearchProject>> groupedProjects = assignedProjects.stream()
            .collect(Collectors.groupingBy(p -> p.getStatus().name()));
            
        model.addAttribute("pendingProjects", groupedProjects.getOrDefault("PENDING", List.of()));
        model.addAttribute("approvedProjects", groupedProjects.getOrDefault("APPROVED", List.of()));
        model.addAttribute("rejectedProjects", groupedProjects.getOrDefault("REJECTED", List.of()));
        
        return "supervisor-dashboard";
    }

    // GET /supervisor/project/{id} → Shows full project detail: metadata, similarity report, extracted text preview, download link
    @GetMapping("/project/{id}")
    public String viewProjectDetail(@PathVariable Long id, Model model) {
        ResearchProject project = projectService.getProjectById(id);
        model.addAttribute("project", project);
        model.addAttribute("feedbackDTO", new FeedbackDTO());
        return "project-detail";
    }

    // POST /supervisor/project/{id}/review → Receives FeedbackDTO, updates project status
    @PostMapping("/project/{id}/review")
    public String reviewProject(@PathVariable Long id, @ModelAttribute FeedbackDTO dto, @SessionAttribute("userId") Long supervisorId, RedirectAttributes redirectAttributes) {
        feedbackService.submitFeedback(id, dto, supervisorId);
        redirectAttributes.addFlashAttribute("message", "Feedback submitted successfully.");
        return "redirect:/supervisor/dashboard";
    }

    // GET /supervisor/projects/search → Filter projects by department, keyword, year
    @GetMapping("/projects/search")
    public String searchProjects(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @SessionAttribute("userId") Long supervisorId,
            Model model) {
            
        List<ResearchProject> filteredProjects = projectService.searchAssignedProjects(supervisorId, department, keyword, year);
        model.addAttribute("projects", filteredProjects);
        return "supervisor-search-results";
    }
}
