package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.repository.ResearchProjectRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ResearchProjectRepository researchProjectRepository;

    public AnalyticsService(ResearchProjectRepository researchProjectRepository) {
        this.researchProjectRepository = researchProjectRepository;
    }

    /**
     * Returns a map of department names to project counts.
     */
    public Map<String, Long> getResearchByDepartment() {
        List<ResearchProject> projects = researchProjectRepository.findAll();
        return projects.stream()
                .filter(p -> p.getDepartment() != null)
                .collect(Collectors.groupingBy(ResearchProject::getDepartment, Collectors.counting()));
    }

    /**
     * Returns monthly submission counts for a given year.
     * Returns a map of month (1-12) to count.
     */
    public Map<Integer, Long> getResearchTrends(int year) {
        List<ResearchProject> projects = researchProjectRepository.findAll();
        return projects.stream()
                .filter(p -> p.getUploadDate() != null && p.getUploadDate().getYear() == year)
                .collect(Collectors.groupingBy(p -> p.getUploadDate().getMonthValue(), Collectors.counting()));
    }

    /**
     * Returns top N research domains (keywords) by project count.
     * Assumes keywords are comma-separated in the keywords field.
     */
    public List<Map.Entry<String, Long>> getTopResearchDomains(int limit) {
        List<ResearchProject> projects = researchProjectRepository.findAll();
        Map<String, Long> domainCounts = new HashMap<>();
        for (ResearchProject project : projects) {
            if (project.getKeywords() != null) {
                String[] keywords = project.getKeywords().split(",");
                for (String keyword : keywords) {
                    String trimmed = keyword.trim().toLowerCase();
                    if (!trimmed.isEmpty()) {
                        domainCounts.put(trimmed, domainCounts.getOrDefault(trimmed, 0L) + 1);
                    }
                }
            }
        }
        return domainCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns projects with similarity score < 40% and viewCount > 100.
     */
    public List<ResearchProject> getHighPotentialProjects() {
        List<ResearchProject> projects = researchProjectRepository.findAll();
        return projects.stream()
                .filter(p -> p.getSimilarityScore() != null && p.getSimilarityScore() < 40.0 && p.getViewCount() != null && p.getViewCount() > 100)
                .collect(Collectors.toList());
    }

    /**
     * Returns all projects where isIncubationFlagged = true.
     */
    public List<ResearchProject> getIncubationCandidates() {
        return researchProjectRepository.findIncubationFlaggedProjects();
    }

    /**
     * Returns all research projects.
     */
    public List<ResearchProject> getAllProjects() {
        return researchProjectRepository.findAll();
    }
}