package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.model.SupervisorFeedback;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ResearchProjectRepository;
import com.chuka.irir.repository.SupervisorFeedbackRepository;
import com.chuka.irir.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    @Autowired
    private SupervisorFeedbackRepository feedbackRepository;

    @Autowired
    private ResearchProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LuceneIndexService luceneIndexService;

    @Transactional
    public void submitFeedback(Long projectId, FeedbackDTO dto, Long supervisorId) {
        ResearchProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new IllegalArgumentException("Supervisor not found"));
        User student = project.getOwner();

        // Save SupervisorFeedback entity
        SupervisorFeedback feedback = new SupervisorFeedback();
        feedback.setResearchProject(project);
        feedback.setSupervisor(supervisor);
        feedback.setComment(dto.getComment());
        feedbackRepository.save(feedback);

        // Actions based on feedback type
        if (dto.getAction() == FeedbackDTO.FeedbackAction.APPROVED) {
            notificationService.sendApprovalEmail(student, project);
        } else if (dto.getAction() == FeedbackDTO.FeedbackAction.FORWARDED_TO_INCUBATION) {
            project.setIsIncubationFlagged(true);
            notificationService.sendIncubationEmail(student, project);
        } else if (dto.getAction() == FeedbackDTO.FeedbackAction.REJECTED) {
            notificationService.sendRejectionEmail(student, project, dto.getComment());
        }

        projectRepository.save(project);
    }
}
