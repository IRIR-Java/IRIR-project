package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.repository.ResearchProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ResearchProjectService {

    @Autowired
    private ResearchProjectRepository projectRepository;

    public List<ResearchProject> getProjectsBySupervisor(Long supervisorId) {
        // Implementation note: Currently SupervisorFeedback links supervisor to project.
        // For simplicity in this stub, we return all projects since the schema may be incomplete.
        return projectRepository.findAll();
    }

    public ResearchProject getProjectById(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }

    public List<ResearchProject> searchAssignedProjects(Long supervisorId, String department, String keyword, Integer year) {
        return getProjectsBySupervisor(supervisorId).stream()
            .filter(p -> department == null || department.isEmpty() || p.getDepartment().equalsIgnoreCase(department))
            .filter(p -> keyword == null || keyword.isEmpty() || (p.getKeywords() != null && p.getKeywords().toLowerCase().contains(keyword.toLowerCase())))
            .filter(p -> year == null || (p.getUploadDate() != null && p.getUploadDate().getYear() == year))
            .collect(Collectors.toList());
    }
}
