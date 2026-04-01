package com.chuka.irir.service;

import com.chuka.irir.dto.CollaboratorDTO;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ProjectRepository;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that recommends potential research collaborators based on keyword
 * and vocabulary overlap between students' projects (UC-04).
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Collect the current student's keyword vector from their projects
 *       and profile researchInterests.</li>
 *   <li>Use Apache Lucene MoreLikeThis to find other students' projects
 *       with similar vocabulary in the full-text index.</li>
 *   <li>Exclude the current student's own projects.</li>
 *   <li>Group results by student and compute a normalized overlap score.</li>
 *   <li>Return the top 10 recommended collaborators ranked by score.</li>
 * </ol>
 */
@Service
public class CollaboratorRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(CollaboratorRecommendationService.class);
    private static final int MAX_RECOMMENDATIONS = 10;

    private final LuceneIndexService luceneIndexService;
    private final ProjectRepository projectRepository;
    private final UserService userService;

    public CollaboratorRecommendationService(LuceneIndexService luceneIndexService,
                                              ProjectRepository projectRepository,
                                              UserService userService) {
        this.luceneIndexService = luceneIndexService;
        this.projectRepository = projectRepository;
        this.userService = userService;
    }

    // ==================== Main Entry Point ====================

    /**
     * Returns the top 10 recommended collaborators for the given student.
     *
     * @param currentStudentId the database ID of the current student
     * @return list of CollaboratorDTOs ranked by keyword overlap score
     */
    @Transactional(readOnly = true)
    public List<CollaboratorDTO> getRecommendations(Long currentStudentId) {
        User currentUser = userService.findById(currentStudentId);

        // 1. Fetch the current student's projects
        List<Project> myProjects = projectRepository.findBySubmittedByOrderByCreatedAtDesc(currentUser);

        // 2. Build the keyword vector from projects + profile researchInterests
        String keywordText = buildKeywordVector(myProjects, currentUser.getResearchInterests());

        if (keywordText.isBlank()) {
            logger.info("No keywords found for student [id={}], cannot recommend collaborators", currentStudentId);
            return List.of();
        }

        // 3. Use Lucene MoreLikeThis to find similar projects
        List<ScoredProject> scoredProjects = findSimilarProjects(keywordText, currentStudentId);

        if (scoredProjects.isEmpty()) {
            logger.info("No similar projects found for student [id={}]", currentStudentId);
            return List.of();
        }

        // 4. Group by student to avoid duplicates
        return groupByStudent(scoredProjects, currentStudentId);
    }

    // ==================== Keyword Vector Construction ====================

    /**
     * Extracts and combines all keyword tokens from a student's projects and
     * profile research interests into a single text string suitable for
     * Lucene MoreLikeThis querying.
     *
     * @param projects          the student's projects
     * @param researchInterests the student's profile research interests (free-text)
     * @return combined keyword text
     */
    String buildKeywordVector(List<Project> projects, String researchInterests) {
        StringBuilder sb = new StringBuilder();

        // Add keywords from all projects
        for (Project project : projects) {
            if (project.getKeywords() != null && !project.getKeywords().isEmpty()) {
                sb.append(String.join(" ", project.getKeywords())).append(" ");
            }
            // Also use the title and abstract for richer vocabulary
            if (project.getTitle() != null) {
                sb.append(project.getTitle()).append(" ");
            }
            if (project.getAbstractText() != null) {
                sb.append(project.getAbstractText()).append(" ");
            }
        }

        // Add profile research interests
        if (researchInterests != null && !researchInterests.isBlank()) {
            sb.append(researchInterests);
        }

        return sb.toString().trim();
    }

    // ==================== Lucene MoreLikeThis Search ====================

    /**
     * Uses Lucene MoreLikeThis to find projects with similar vocabulary.
     *
     * @param keywordText      combined keyword text from the current student
     * @param currentStudentId the current student's ID (to exclude their projects)
     * @return list of scored projects from other students
     */
    private List<ScoredProject> findSimilarProjects(String keywordText, Long currentStudentId) {
        try {
            if (luceneIndexService.getDirectory() == null) {
                logger.warn("Lucene directory not initialized");
                return List.of();
            }

            IndexReader reader;
            try {
                reader = DirectoryReader.open(luceneIndexService.getDirectory());
            } catch (IOException e) {
                logger.warn("Cannot open Lucene index: {}", e.getMessage());
                return List.of();
            }

            if (reader.numDocs() == 0) {
                reader.close();
                return List.of();
            }

            IndexSearcher searcher = new IndexSearcher(reader);
            Analyzer analyzer = luceneIndexService.getAnalyzer();

            // Configure MoreLikeThis
            MoreLikeThis mlt = new MoreLikeThis(reader);
            mlt.setAnalyzer(analyzer);
            mlt.setFieldNames(new String[]{
                    LuceneIndexService.FIELD_FULL_CONTENT,
                    LuceneIndexService.FIELD_KEYWORDS,
                    LuceneIndexService.FIELD_TITLE
            });
            mlt.setMinTermFreq(1);
            mlt.setMinDocFreq(1);
            mlt.setMinWordLen(3);
            mlt.setMaxQueryTerms(150);

            // Build query from the keyword text
            Query mltQuery = mlt.like(
                    LuceneIndexService.FIELD_FULL_CONTENT,
                    new StringReader(keywordText));

            TopDocs topDocs = searcher.search(mltQuery, 50); // Fetch more for filtering

            List<ScoredProject> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String projectIdStr = doc.get(LuceneIndexService.FIELD_PROJECT_ID);

                if (projectIdStr == null) continue;

                long projectId;
                try {
                    projectId = Long.parseLong(projectIdStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                // Look up the project in DB to check ownership
                Optional<Project> projectOpt = projectRepository.findById(projectId);
                if (projectOpt.isEmpty()) continue;

                Project project = projectOpt.get();

                // Skip the current student's own projects
                if (project.getSubmittedBy() != null &&
                    project.getSubmittedBy().getId().equals(currentStudentId)) {
                    continue;
                }

                String title = doc.get(LuceneIndexService.FIELD_TITLE);
                String keywords = doc.get(LuceneIndexService.FIELD_KEYWORDS);

                results.add(new ScoredProject(
                        projectId,
                        title != null ? title : project.getTitle(),
                        keywords != null ? keywords : "",
                        scoreDoc.score,
                        project.getSubmittedBy()
                ));
            }

            reader.close();

            logger.debug("MoreLikeThis search found {} candidate projects for student [id={}]",
                    results.size(), currentStudentId);

            return results;

        } catch (IOException e) {
            logger.error("Lucene MoreLikeThis search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ==================== Grouping by Student ====================

    /**
     * Groups scored projects by student to produce one recommendation per collaborator.
     * Aggregates matching project titles and computes a combined overlap score.
     *
     * @param scoredProjects   the list of matched projects with Lucene scores
     * @param currentStudentId the current student's ID (to exclude)
     * @return top 10 CollaboratorDTOs ranked by overlap score
     */
    List<CollaboratorDTO> groupByStudent(List<ScoredProject> scoredProjects, Long currentStudentId) {
        // Group by student user ID
        Map<Long, List<ScoredProject>> byStudent = scoredProjects.stream()
                .filter(sp -> sp.owner != null)
                .filter(sp -> !sp.owner.getId().equals(currentStudentId))
                .collect(Collectors.groupingBy(sp -> sp.owner.getId()));

        // Find the maximum total score for normalization
        double maxTotalScore = byStudent.values().stream()
                .mapToDouble(projects -> projects.stream().mapToDouble(sp -> sp.score).sum())
                .max()
                .orElse(1.0);

        if (maxTotalScore == 0.0) maxTotalScore = 1.0;

        List<CollaboratorDTO> collaborators = new ArrayList<>();
        final double normFactor = maxTotalScore;

        for (Map.Entry<Long, List<ScoredProject>> entry : byStudent.entrySet()) {
            List<ScoredProject> projects = entry.getValue();
            User owner = projects.get(0).owner;

            // Sum scores from all matching projects
            double totalScore = projects.stream().mapToDouble(sp -> sp.score).sum();

            // Normalize to 0.0–1.0
            double normalizedScore = Math.min(totalScore / normFactor, 1.0);
            normalizedScore = Math.round(normalizedScore * 100.0) / 100.0;

            List<String> matchingTitles = projects.stream()
                    .map(sp -> sp.title)
                    .distinct()
                    .collect(Collectors.toList());

            collaborators.add(CollaboratorDTO.builder()
                    .userId(owner.getId())
                    .fullName(owner.getFullName())
                    .regNumber(owner.getStudentId())
                    .department(owner.getDepartment())
                    .researchInterests(owner.getResearchInterests())
                    .matchingProjects(matchingTitles)
                    .overlapScore(normalizedScore)
                    .contactEmail(owner.getEmail())
                    .build());
        }

        // Sort by overlap score descending
        collaborators.sort((a, b) -> Double.compare(b.getOverlapScore(), a.getOverlapScore()));

        // Return top 10
        if (collaborators.size() > MAX_RECOMMENDATIONS) {
            return collaborators.subList(0, MAX_RECOMMENDATIONS);
        }

        return collaborators;
    }

    // ==================== Internal Data Class ====================

    /**
     * Internal holder for a Lucene-scored project result.
     */
    static class ScoredProject {
        final long projectId;
        final String title;
        final String keywords;
        final float score;
        final User owner;

        ScoredProject(long projectId, String title, String keywords, float score, User owner) {
            this.projectId = projectId;
            this.title = title;
            this.keywords = keywords;
            this.score = score;
            this.owner = owner;
        }
    }
}
