package com.chuka.irir.repository;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectScore;
import com.chuka.irir.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectScoreRepository extends JpaRepository<ProjectScore, Long> {

    List<ProjectScore> findByProject(Project project);

    List<ProjectScore> findByProjectId(Long projectId);

    List<ProjectScore> findByScorer(User scorer);

    Optional<ProjectScore> findByProjectAndScorer(Project project, User scorer);

    @Query("SELECT AVG(ps.overallScore) FROM ProjectScore ps WHERE ps.project.id = :projectId")
    Double findAverageScoreByProjectId(Long projectId);

    @Query("SELECT ps.project.id, AVG(ps.overallScore) as avgScore FROM ProjectScore ps " +
           "GROUP BY ps.project.id ORDER BY avgScore DESC")
    List<Object[]> findTopScoredProjectIds(Pageable pageable);
}
