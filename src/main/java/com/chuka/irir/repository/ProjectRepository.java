package com.chuka.irir.repository;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Project} entities.
 *
 * Provides queries for the student dashboard, supervisor review queue,
 * directorate analytics, and similarity detection workflows.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Find all projects submitted by a specific student. */
    List<Project> findBySubmittedByOrderByCreatedAtDesc(User submittedBy);

    /** Find all projects assigned to a specific supervisor. */
    List<Project> findBySupervisorOrderBySubmittedAtDesc(User supervisor);

    /** Find all projects with a specific status. */
    List<Project> findByStatusOrderBySubmittedAtDesc(ProjectStatus status);

    /** Find all projects with a specific status, excluding a given project. Used for similarity checks. */
    @Query("SELECT p FROM Project p WHERE p.status <> 'DRAFT' AND p.id <> :excludeId AND p.extractedText IS NOT NULL")
    List<Project> findAllForSimilarityCheck(@Param("excludeId") Long excludeId);

    /** Count projects by status. Used for analytics dashboard. */
    long countByStatus(ProjectStatus status);

    /** Find projects by keyword. Used for collaborator recommendations. */
    @Query("SELECT DISTINCT p FROM Project p JOIN p.keywords k WHERE LOWER(k) IN :keywords AND p.submittedBy <> :excludeUser")
    List<Project> findByKeywordsAndNotSubmittedBy(
            @Param("keywords") List<String> keywords,
            @Param("excludeUser") User excludeUser
    );

    /** Count total projects in the system. */
    @Query("SELECT COUNT(p) FROM Project p WHERE p.status <> 'DRAFT'")
    long countSubmittedProjects();

    /** Search projects by title or abstract (case-insensitive). */
    @Query("SELECT p FROM Project p WHERE " +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Project> searchByTitleOrAbstract(@Param("term") String term);

    /** Find projects for a specific academic year. */
    List<Project> findByAcademicYearOrderByCreatedAtDesc(Integer academicYear);
}
