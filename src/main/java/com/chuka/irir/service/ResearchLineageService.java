package com.chuka.irir.service;

import com.chuka.irir.dto.ResearchLineageDTO;
import com.chuka.irir.dto.SimilarityResult;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ResearchLineage;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.ResearchLineageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ResearchLineageService {

    private static final Logger log = LoggerFactory.getLogger(ResearchLineageService.class);
    private static final double MIN_LINEAGE_SCORE = 0.10;
    private static final double BUILDS_ON_THRESHOLD = 0.25;

    private final ResearchLineageRepository lineageRepository;
    private final ProjectRepository projectRepository;

    /**
     * Builds research lineage links between a newly submitted project and
     * previously indexed projects that overlap meaningfully (score in [0.10, 0.70)).
     */
    public void buildLineage(Project newProject, SimilarityResult result) {
        if (result == null || result.getMatchedProjects() == null) return;
        try {
            for (SimilarityResult.MatchedProject match : result.getMatchedProjects()) {
                double score = match.getSimilarityScore() != null ? match.getSimilarityScore() : 0.0;

                // Only create lineage for "related but not duplicate" range
                if (score < MIN_LINEAGE_SCORE || score >= 0.70) continue;

                Long ancestorId = match.getProjectId();
                if (ancestorId == null) continue;

                projectRepository.findById(ancestorId).ifPresent(ancestor -> {
                    // Avoid duplicate lineage entries
                    if (lineageRepository.existsByDescendantProjectAndAncestorProject(newProject, ancestor)) {
                        return;
                    }

                    String linkType = score >= BUILDS_ON_THRESHOLD ? "BUILDS_ON" : "RELATED_TO";

                    ResearchLineage lineage = ResearchLineage.builder()
                            .descendantProject(newProject)
                            .ancestorProject(ancestor)
                            .relatednessScore(score)
                            .linkType(linkType)
                            .build();

                    lineageRepository.save(lineage);
                    log.debug("Lineage created: project [{}] {} [{}] (score={})",
                            newProject.getId(), linkType, ancestorId, score);
                });
            }
        } catch (Exception e) {
            log.error("Failed to build research lineage for project {}: {}", newProject.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ResearchLineageDTO> getAncestorsFor(Long projectId) {
        return projectRepository.findById(projectId)
                .map(p -> lineageRepository.findByDescendantProjectOrderByRelatednessScoreDesc(p)
                        .stream()
                        .limit(5)
                        .map(l -> toDTO(l, true))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ResearchLineageDTO> getDescendantsOf(Long projectId) {
        return projectRepository.findById(projectId)
                .map(p -> lineageRepository.findByAncestorProjectOrderByLinkedAtDesc(p)
                        .stream()
                        .limit(5)
                        .map(l -> toDTO(l, false))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    private ResearchLineageDTO toDTO(ResearchLineage l, boolean isAncestor) {
        Project related = isAncestor ? l.getAncestorProject() : l.getDescendantProject();
        String author = related.getSubmittedBy() != null
                ? related.getSubmittedBy().getFullName()
                : "Unknown";
        return ResearchLineageDTO.builder()
                .projectId(related.getId())
                .title(related.getTitle())
                .authorName(author)
                .academicYear(related.getAcademicYear())
                .relatednessScore(l.getRelatednessScore())
                .linkType(l.getLinkType())
                .build();
    }
}
