package com.chuka.irir.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object encapsulating the results of a similarity check (UC-02).
 *
 * <p>After a new project's text is compared against all indexed documents using
 * TF-IDF cosine similarity, this object carries:
 * <ul>
 *   <li>The highest similarity score found ({@code maxSimilarityScore})</li>
 *   <li>Whether the score exceeds the flagging threshold ({@code aboveThreshold})</li>
 *   <li>A human-readable verdict label ({@code verdictLabel})</li>
 *   <li>A list of matched projects with individual scores ({@code matchedProjects})</li>
 * </ul>
 *
 * <h3>Threshold Behavior</h3>
 * <table>
 *   <tr><th>Score Range</th><th>Verdict</th><th>System Action</th></tr>
 *   <tr><td>&lt; 0.40</td><td>Original Work</td><td>Auto-approve for indexing</td></tr>
 *   <tr><td>0.40 – 0.69</td><td>Similar Work Detected</td><td>Warn student, allow submission</td></tr>
 *   <tr><td>&ge; 0.70</td><td>Potential Duplicate</td><td>Hold for supervisor review, send email</td></tr>
 * </table>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityResult {

    /** The highest cosine similarity score among all comparisons (0.0 – 1.0). */
    private Double maxSimilarityScore;

    /** True if {@code maxSimilarityScore} is at or above the flagging threshold (default 0.70). */
    private Boolean aboveThreshold;

    /**
     * Human-readable verdict string:
     *   "Original Work" | "Similar Work Detected" | "Potential Duplicate"
     */
    private String verdictLabel;

    /** List of projects that matched with a non-zero similarity score. */
    @Builder.Default
    private List<MatchedProject> matchedProjects = new ArrayList<>();

    // ==================== Inner DTO ====================

    /**
     * Represents a single matched project from the similarity search.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchedProject {

        /** Database ID of the matched project. */
        private Long projectId;

        /** Title of the matched project. */
        private String title;

        /** Author (student) name of the matched project. */
        private String author;

        /** Cosine similarity score against the new document (0.0 – 1.0). */
        private Double similarityScore;

        /** URL path to view the matched project (e.g., "/student/projects/42"). */
        private String projectUrl;
    }
}
