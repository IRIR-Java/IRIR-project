package com.chuka.irir.repository;

import com.chuka.irir.model.FeedbackAction;
import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.model.SupervisorFeedback;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupervisorFeedbackRepository extends JpaRepository<SupervisorFeedback, Long> {

    List<SupervisorFeedback> findByResearchProjectOrderByCreatedAtDesc(ResearchProject researchProject);

    List<SupervisorFeedback> findBySupervisorOrderByCreatedAtDesc(User supervisor);

    List<SupervisorFeedback> findByAction(FeedbackAction action);
}
