package com.chuka.irir.repository;

import com.chuka.irir.model.ProjectInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectInsightRepository extends JpaRepository<ProjectInsight, Long> {
    Optional<ProjectInsight> findByProjectId(Long projectId);
}
