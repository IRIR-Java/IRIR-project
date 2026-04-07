package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores AI-generated insights for a project.
 */
@Entity
@Table(name = "project_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String gaps;

    @Column(columnDefinition = "TEXT")
    private String suggestedKeywords;

    @Column(columnDefinition = "TEXT")
    private String nextSteps;

    @Column(columnDefinition = "TEXT")
    private String riskFlags;

    @Column(name = "novelty_score")
    private Double noveltyScore;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(length = 5)
    private String qualityGrade;

    private Integer originalityScore;

    private Integer technicalDepthScore;

    private Integer writingQualityScore;

    private Integer methodologyScore;

    private Integer practicalApplicabilityScore;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(columnDefinition = "TEXT")
    private String detailedAnalysis;
}
