package com.chuka.irir.repository;

import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResearchProjectRepository extends JpaRepository<ResearchProject, Long> {

    List<ResearchProject> findByOwnerOrderByUploadDateDesc(User owner);

    List<ResearchProject> findByStatusOrderByUploadDateDesc(ProjectStatus status);
    
    List<ResearchProject> findByDepartment(String department);

    @Query("SELECT r FROM ResearchProject r WHERE r.isIncubationFlagged = true")
    List<ResearchProject> findIncubationFlaggedProjects();

    @Query("SELECT r FROM ResearchProject r WHERE LOWER(r.title) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(r.keywords) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<ResearchProject> searchByTitleOrKeyword(@Param("term") String term);
}
