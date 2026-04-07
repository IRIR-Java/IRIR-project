package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scorer_id", nullable = false)
    private User scorer;

    @Column(nullable = false)
    private Integer originality;

    @Column(nullable = false)
    private Integer methodology;

    @Column(nullable = false)
    private Integer presentation;

    @Column(nullable = false)
    private Integer impact;

    private Double overallScore;

    @Column(columnDefinition = "TEXT")
    private String comments;

    private LocalDateTime scoredAt;

    @PrePersist
    public void computeScore() {
        this.overallScore = originality * 0.30 + methodology * 0.25 + presentation * 0.20 + impact * 0.25;
        if (this.scoredAt == null) this.scoredAt = LocalDateTime.now();
    }

    @PreUpdate
    public void recomputeScore() {
        this.overallScore = originality * 0.30 + methodology * 0.25 + presentation * 0.20 + impact * 0.25;
    }
}
