package com.chuka.irir.controller;

import com.chuka.irir.dto.SearchDTO;
import com.chuka.irir.model.Project;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.service.ProjectSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class PublicGalleryController {

    private final ProjectRepository projectRepository;
    private final ProjectSearchService projectSearchService;

    @GetMapping
    public String listGallery(SearchDTO searchDTO, Model model) {
        Page<ProjectSearchService.SearchResultDTO> results = projectSearchService.search(searchDTO);
        model.addAttribute("results", results);
        model.addAttribute("searchCriteria", searchDTO != null ? searchDTO : new SearchDTO());
        return "search-results";
    }

    @GetMapping("/{id}")
    public String viewProjectDetails(@PathVariable Long id, Model model) {
        Project project = projectRepository.findById(id).orElseThrow();
        
        // Increment view count on page load
        project.setViewCount(project.getViewCount() + 1);
        projectRepository.save(project);

        String keywords = project.getKeywords() != null ? String.join(" ", project.getKeywords()) : "";
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setCategory(keywords);
        
        List<ProjectSearchService.SearchResultDTO> similarProjects = projectSearchService.search(searchDTO)
                .getContent()
                .stream()
                .filter(p -> !p.projectId().equals(id))
                .limit(5)
                .collect(Collectors.toList());

        model.addAttribute("project", project);
        model.addAttribute("similarProjects", similarProjects);

        return "project-details"; // Required route, HTML might be auto-generated or external
    }
}
