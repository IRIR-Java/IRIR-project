package com.chuka.irir.repository;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.model.SimilarityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SimilarityReport} entities.
 */
@Repository
public interface SimilarityReportRepository extends JpaRepository<SimilarityReport, Long> {

    Optional<SimilarityReport> findByResearchProject(ResearchProject researchProject);

    /** Eagerly fetch reports with target project and its author - avoids LazyInitializationException. */
    @Query("SELECT sr FROM SimilarityReport sr " +
           "JOIN FETCH sr.targetProject tp " +
           "LEFT JOIN FETCH tp.submittedBy " +
           "WHERE sr.sourceProject.id = :projectId " +
           "ORDER BY sr.similarityScore DESC")
    List<SimilarityReport> findBySourceProjectIdWithDetails(@Param("projectId") Long projectId);

    /** Find all flagged similarity reports (score above threshold). */
    List<SimilarityReport> findByFlaggedTrueOrderBySimilarityScoreDesc();

    List<SimilarityReport> findBySimilarityScoreGreaterThanEqual(Double threshold);

    List<SimilarityReport> findAllByOrderByGeneratedAtDesc();

    /** Count flagged reports. Used for dashboard metrics. */
    long countByFlaggedTrue();

    /** Find the maximum similarity score for a given source project. */
    @Query("SELECT MAX(sr.similarityScore) FROM SimilarityReport sr WHERE sr.sourceProject.id = :projectId")
    Double findMaxSimilarityScoreByProjectId(@Param("projectId") Long projectId);
}
