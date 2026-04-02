package com.chuka.irir.repository;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ResearchLineage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchLineageRepository extends JpaRepository<ResearchLineage, Long> {

    List<ResearchLineage> findByDescendantProjectOrderByRelatednessScoreDesc(Project p);

    List<ResearchLineage> findByAncestorProjectOrderByLinkedAtDesc(Project p);

    boolean existsByDescendantProjectAndAncestorProject(Project d, Project a);
}
