package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "similarity_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @Column(nullable = false)
    private Double similarityScore;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(columnDefinition = "JSON")
    private String matchedProjects;

    // Relationships
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "research_project_id", nullable = false)
    private ResearchProject researchProject;
}
