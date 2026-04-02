package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "similarity_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double similarityScore;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(columnDefinition = "json") // safer
    private String matchedProjects;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "title_similarity_score")
    private Double titleSimilarityScore;

    @Column(name = "abstract_similarity_score")
    private Double abstractSimilarityScore;

    @Column(name = "keywords_similarity_score")
    private Double keywordsSimilarityScore;

    @Column(name = "weighted_composite_score")
    private Double weightedCompositeScore;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_project_id", nullable = false)
    private Project sourceProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_project_id", nullable = false)
    private Project targetProject;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "research_project_id")
    private ResearchProject researchProject;
}
