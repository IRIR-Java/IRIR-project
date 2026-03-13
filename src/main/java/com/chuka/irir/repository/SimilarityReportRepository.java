package com.chuka.irir.repository;

import com.chuka.irir.model.SimilarityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SimilarityReport} entities.
 */
@Repository
public interface SimilarityReportRepository extends JpaRepository<SimilarityReport, Long> {

    /** Find all similarity reports for a source project, ordered by score descending. */
    List<SimilarityReport> findBySourceProjectIdOrderBySimilarityScoreDesc(Long sourceProjectId);

    /** Find all flagged similarity reports (score above threshold). */
    List<SimilarityReport> findByFlaggedTrueOrderBySimilarityScoreDesc();

    /** Find the highest similarity score for a given source project. */
    @Query("SELECT MAX(sr.similarityScore) FROM SimilarityReport sr WHERE sr.sourceProject.id = :projectId")
    Double findMaxSimilarityScoreByProjectId(@Param("projectId") Long projectId);

    /** Count flagged reports. Used for dashboard metrics. */
    long countByFlaggedTrue();
}
