package com.chuka.irir.service;

import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.repository.ResearchProjectRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing research analytics used by the Directorate dashboard.
 *
 * <p>All methods operate on the full {@link ResearchProject} dataset via
 * {@link ResearchProjectRepository}. Results are returned as simple maps
 * and lists so the controller can pass them directly to Thymeleaf / Chart.js.</p>
 */
@Service
public class AnalyticsService {

    private final ResearchProjectRepository researchProjectRepository;

    public AnalyticsService(ResearchProjectRepository researchProjectRepository) {
        this.researchProjectRepository = researchProjectRepository;
    }

    // ==================== Department Distribution ====================

    /**
     * Counts the number of research projects per department.
     *
     * @return map of department name → project count
     */
    public Map<String, Long> getResearchByDepartment() {
        return researchProjectRepository.findAll().stream()
                .filter(p -> p.getDepartment() != null && !p.getDepartment().isBlank())
                .collect(Collectors.groupingBy(
                        ResearchProject::getDepartment,
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    // ==================== Monthly Submission Trends ====================

    /**
     * Returns the number of project submissions per month for a given year.
     * Always returns all 12 months (defaulting to 0 if no submissions).
     *
     * @param year the calendar year to filter by
     * @return ordered map of month (1–12) → submission count
     */
    public Map<Integer, Long> getResearchTrends(int year) {
        Map<Integer, Long> trends = researchProjectRepository.findAll().stream()
                .filter(p -> p.getUploadDate() != null && p.getUploadDate().getYear() == year)
                .collect(Collectors.groupingBy(
                        p -> p.getUploadDate().getMonthValue(),
                        Collectors.counting()));

        // Ensure all 12 months are present in order
        Map<Integer, Long> normalized = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            normalized.put(month, trends.getOrDefault(month, 0L));
        }
        return normalized;
    }

    // ==================== Top Research Domains (Keywords) ====================

    /**
     * Extracts individual keywords from each project's comma-separated
     * {@code keywords} field, counts their frequency, and returns the
     * top N keywords ranked by occurrence.
     *
     * <p>This provides a true "research domain" distribution rather than
     * grouping by department.</p>
     *
     * @param limit maximum number of domains to return
     * @return ordered map of keyword → frequency, most frequent first
     */
    public Map<String, Long> getTopResearchDomains(int limit) {
        // Explode each project's comma-separated keywords into individual tokens
        Map<String, Long> keywordCounts = researchProjectRepository.findAll().stream()
                .filter(p -> p.getKeywords() != null && !p.getKeywords().isBlank())
                .flatMap(p -> Arrays.stream(p.getKeywords().split(",")))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

        // Sort by count descending, then limit
        return keywordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    // ==================== High-Potential Projects ====================

    /**
     * Returns projects with high originality AND high engagement:
     * similarity score < 40% (i.e., mostly unique content) and
     * view count > 100 (strong community interest).
     *
     * @return list of high-potential {@link ResearchProject} entities
     */
    public List<ResearchProject> getHighPotentialProjects() {
        return researchProjectRepository
                .findBySimilarityScoreLessThanAndViewCountGreaterThan(0.40, 100);
    }

    // ==================== Incubation Candidates ====================

    /**
     * Returns all projects that have been flagged for incubation
     * by the Directorate.
     *
     * @return list of incubation-flagged {@link ResearchProject} entities
     */
    public List<ResearchProject> getIncubationCandidates() {
        return researchProjectRepository.findByIsIncubationFlaggedTrue();
    }

    // ==================== All Projects ====================

    /**
     * Returns every research project in the system (used for Excel export).
     *
     * @return complete list of {@link ResearchProject} entities
     */
    public List<ResearchProject> getAllProjects() {
        return researchProjectRepository.findAll();
    }

    // ==================== Summary Statistics ====================

    /**
     * Computes aggregate counts for the dashboard summary stat cards.
     *
     * @return map with keys: {@code totalProjects}, {@code totalApproved},
     *         {@code totalPending}, {@code totalIncubation}
     */
    public Map<String, Long> getSummaryStats() {
        List<ResearchProject> all = researchProjectRepository.findAll();

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("totalProjects", (long) all.size());
        stats.put("totalApproved", all.stream()
                .filter(p -> p.getStatus() == ProjectStatus.APPROVED)
                .count());
        stats.put("totalPending", all.stream()
                .filter(p -> p.getStatus() == ProjectStatus.PENDING)
                .count());
        stats.put("totalIncubation", all.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsIncubationFlagged()))
                .count());
        return stats;
    }
}
