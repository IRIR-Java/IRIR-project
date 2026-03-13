package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a similarity comparison between two {@link Project}s.
 *
 * Generated automatically when a project is submitted (UC-02).
 * Uses TF-IDF cosine similarity via Apache Lucene to calculate a similarity score
 * between the source project's extracted text and every other project in the repository.
 *
 * <p>Scores range from 0.0 (no similarity) to 1.0 (identical).
 * Projects with scores above a configurable threshold (e.g., 0.7) are automatically
 * flagged for review.</p>
 */
@Entity
@Table(name = "similarity_reports",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"source_project_id", "target_project_id"}
       ))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The newly submitted project being checked for similarity. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_project_id", nullable = false)
    private Project sourceProject;

    /** The existing project in the repository being compared against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_project_id", nullable = false)
    private Project targetProject;

    /**
     * Cosine similarity score between the two projects.
     * Range: 0.0 (completely different) to 1.0 (identical).
     */
    @Column(name = "similarity_score", nullable = false)
    private Double similarityScore;

    /** Whether this comparison was flagged (score above threshold). */
    @Column(nullable = false)
    @Builder.Default
    private boolean flagged = false;

    /** Timestamp when the similarity check was performed. */
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        this.generatedAt = LocalDateTime.now();
    }
}
