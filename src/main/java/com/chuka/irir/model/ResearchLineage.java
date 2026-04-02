package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "research_lineage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"descendantProject", "ancestorProject"})
public class ResearchLineage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "descendant_project_id", nullable = false)
    private Project descendantProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancestor_project_id", nullable = false)
    private Project ancestorProject;

    @Column(nullable = false)
    private double relatednessScore;

    @Column(length = 20)
    private String linkType;

    @CreationTimestamp
    private LocalDateTime linkedAt;
}
