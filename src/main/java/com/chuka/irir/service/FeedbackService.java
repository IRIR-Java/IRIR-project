package com.irir.service;

import com.irir.dto.FeedbackDTO;
import com.irir.model.ResearchProject;
import com.irir.model.SupervisorFeedback;
import com.irir.model.User;
import com.irir.repository.ResearchProjectRepository;
import com.irir.repository.SupervisorFeedbackRepository;
import com.irir.repository.UserRepository;
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
        User student = project.getStudent();
        
        // Save SupervisorFeedback entity
        SupervisorFeedback feedback = new SupervisorFeedback();
        feedback.setProject(project);
        feedback.setSupervisor(supervisor);
        feedback.setAction(dto.getAction().name());
        feedback.setComment(dto.getComment());
        feedbackRepository.save(feedback);
        
        // Update ResearchProject status accordingly
        project.setStatus(dto.getAction().name());
        
        // Actions based on feedback type
        if (dto.getAction() == FeedbackDTO.FeedbackAction.APPROVED) {
            luceneIndexService.indexDocument(project);
            notificationService.sendApprovalEmail(student, project);
        } else if (dto.getAction() == FeedbackDTO.FeedbackAction.FORWARDED_TO_INCUBATION) {
            project.setIncubationFlagged(true);
            notificationService.sendIncubationEmail(student, project);
        } else if (dto.getAction() == FeedbackDTO.FeedbackAction.REJECTED) {
            notificationService.sendRejectionEmail(student, project, dto.getComment());
        }
        
        projectRepository.save(project);
    }
}
