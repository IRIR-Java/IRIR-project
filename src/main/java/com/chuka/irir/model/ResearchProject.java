package com.chuka.irir.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "research_projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResearchProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long projectId;

    @NotBlank(message = "Title is required")
    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String abstractText;

    @Column(length = 255)
    private String keywords;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String supervisorName;

    @Column(length = 255)
    private String filePath;

    @Column(length = 255)
    private String sourceCodePath;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PENDING;

    @Builder.Default
    private Integer viewCount = 0;

    @Builder.Default
    private Integer downloadCount = 0;

    private Double similarityScore;

    @Builder.Default
    private Boolean isIncubationFlagged = false;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "researchProject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SimilarityReport> similarityReports = new ArrayList<>();

    @OneToMany(mappedBy = "researchProject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SupervisorFeedback> supervisorFeedbacks = new ArrayList<>();
}
