package com.chuka.irir.controller;

import com.chuka.irir.dto.SearchDTO;
import com.chuka.irir.dto.StudentInsights;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.ProjectSearchService;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentDashboardController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectSearchService projectSearchService;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName()).orElseThrow();
    }

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication authentication, 
            Model model,
            @ModelAttribute("searchCriteria") SearchDTO searchCriteria,
            @RequestParam(name = "tab", defaultValue = "dashboard") String tab) {
            
        User student = getCurrentUser(authentication);
        List<Project> projects = projectRepository.findBySubmittedByOrderByCreatedAtDesc(student);
        
        long totalViews = projects.stream().mapToLong(Project::getViewCount).sum();
        long totalDownloads = projects.stream().mapToLong(Project::getDownloadCount).sum();
        long pendingReviews = projects.stream()
                .filter(p -> p.getStatus().name().equals("UNDER_REVIEW"))
                .count();

        boolean hasWarnings = projects.stream().anyMatch(p -> p.getStatus().name().equals("FLAGGED"));

        model.addAttribute("student", student);
        model.addAttribute("projects", projects);
        model.addAttribute("totalProjects", projects.size());
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("totalDownloads", totalDownloads);
        model.addAttribute("pendingReviews", pendingReviews);
        model.addAttribute("hasWarnings", hasWarnings);

        model.addAttribute("insights", buildInsights(projects));
        
        String collabProject = projects.isEmpty() ? "your project" : projects.get(0).getTitle();
        model.addAttribute("collabRequests", List.of(
            "Alice (Software Eng) wants to collaborate on " + collabProject,
            "Dr. Smith left a comment on your abstract"
        ));

        // Combine Gallery Search logic into Dashboard
        boolean isSearchActive = (searchCriteria.getQuery() != null && !searchCriteria.getQuery().isEmpty()) || 
                                 (searchCriteria.getDepartment() != null && !searchCriteria.getDepartment().isEmpty()) || 
                                 searchCriteria.getYear() != null || 
                                 (searchCriteria.getAuthor() != null && !searchCriteria.getAuthor().isEmpty());
        
        String activeTab = isSearchActive ? "gallery" : tab;
        model.addAttribute("activeTab", activeTab);

        Page<ProjectSearchService.SearchResultDTO> results = projectSearchService.search(searchCriteria);
        model.addAttribute("results", results);

        return "student-dashboard";
    }

    private StudentInsights buildInsights(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return new StudentInsights(
                    List.of("Start with a clear problem statement", "Collect at least 3 domain keywords"),
                    List.of("Upload your first project draft", "Add an abstract to improve discoverability")
            );
        }

        var keywordCounts = projects.stream()
                .filter(p -> p.getKeywords() != null)
                .flatMap(p -> p.getKeywords().stream())
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.groupingBy(k -> k, java.util.stream.Collectors.counting()));

        List<String> trendingTopics = keywordCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());

        List<String> actionItems = new java.util.ArrayList<>();
        if (projects.stream().anyMatch(p -> p.getStatus().name().equals("DRAFT"))) {
            actionItems.add("Finish and submit your draft project");
        }
        if (projects.stream().anyMatch(p -> p.getStatus().name().equals("FLAGGED"))) {
            actionItems.add("Review similarity warnings and adjust citations");
        }
        if (projects.stream().anyMatch(p -> !StringUtils.hasText(p.getAbstractText()))) {
            actionItems.add("Add or expand abstracts for incomplete projects");
        }
        if (trendingTopics.isEmpty()) {
            actionItems.add("Add 3-5 keywords to improve discovery and recommendations");
        }
        if (actionItems.isEmpty()) {
            actionItems.add("Consider refining methodology and results sections");
        }

        return new StudentInsights(trendingTopics, actionItems);
    }

    @GetMapping("/profile")
    public String viewProfile(Authentication authentication, Model model) {
        model.addAttribute("user", getCurrentUser(authentication));
        return "student-profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            Authentication authentication,
            @RequestParam("researchInterests") String researchInterests,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "profilePhotoUrl", required = false) String profilePhotoUrl,
            RedirectAttributes redirectAttributes) {

        User student = getCurrentUser(authentication);
        student.setResearchInterests(researchInterests);
        student.setPhoneNumber(phoneNumber);
        if (profilePhotoUrl != null && !profilePhotoUrl.isBlank()) {
            student.setProfilePhotoUrl(profilePhotoUrl);
        }
        userRepository.save(student);

        redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
        return "redirect:/student/profile";
    }

    @GetMapping("/project/{id}/analytics")
    public String projectAnalytics(@PathVariable Long id, Authentication authentication, Model model) {
        User student = getCurrentUser(authentication);
        Project project = projectRepository.findById(id).orElseThrow();

        if (!project.getSubmittedBy().getId().equals(student.getId())) {
            return "redirect:/student/dashboard";
        }

        String category = project.getKeywords() != null && !project.getKeywords().isEmpty() 
            ? String.join(" ", project.getKeywords()) 
            : "";
        
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setCategory(category);
        Page<ProjectSearchService.SearchResultDTO> suggestions = projectSearchService.search(searchDTO);
        
        List<ProjectSearchService.SearchResultDTO> similarProjects = suggestions.getContent()
            .stream()
            .filter(p -> !p.projectId().equals(id))
            .limit(5)
            .collect(Collectors.toList());

        model.addAttribute("project", project);
        model.addAttribute("similarProjects", similarProjects);

        return "project-analytics"; // Not requested as an HTML to show, but required to map to view.
    }
}
