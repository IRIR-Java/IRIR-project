package com.chuka.irir.service;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    // ==================== Project (main submission flow) ====================

    /** Notifies a supervisor when a student submits a project for review. */
    @Async
    public void sendProjectSubmittedNotification(User supervisor, Project project) {
        if (supervisor == null) return;
        Context context = new Context();
        context.setVariable("supervisorName", supervisor.getFullName());
        context.setVariable("studentName", project.getSubmittedBy().getFullName());
        context.setVariable("projectTitle", project.getTitle());
        context.setVariable("projectId", project.getId());
        String body = templateEngine.process("email/project-submitted", context);
        sendHtmlMessage(supervisor.getEmail(), "New Project Submitted for Review: " + project.getTitle(), body);
    }

    /** Warns a student that their project has moderate similarity to existing work. */
    @Async
    public void sendSimilarityWarningNotification(User student, Project project, double score) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        context.setVariable("scorePercent", String.format("%.1f%%", score * 100));
        String body = templateEngine.process("email/similarity-warning", context);
        sendHtmlMessage(student.getEmail(), "Similarity Warning: " + project.getTitle(), body);
    }

    /** Notifies a supervisor when a project is flagged as a potential duplicate (score ≥ 70%). */
    @Async
    public void sendFlaggedProjectNotification(User supervisor, User student, Project project, double score) {
        if (supervisor == null) return;
        Context context = new Context();
        context.setVariable("supervisorName", supervisor.getFullName());
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        context.setVariable("scorePercent", String.format("%.1f%%", score * 100));
        context.setVariable("projectId", project.getId());
        String body = templateEngine.process("email/project-flagged", context);
        sendHtmlMessage(supervisor.getEmail(), "FLAGGED Project Requires Review: " + project.getTitle(), body);
    }

    /** Notifies a student when their project is approved. */
    @Async
    public void sendApprovalNotification(User student, Project project) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        String body = templateEngine.process("email/approval-email", context);
        sendHtmlMessage(student.getEmail(), "Project Approved: " + project.getTitle(), body);
    }

    /** Notifies a student when their project is rejected with a reason. */
    @Async
    public void sendRejectionNotification(User student, Project project, String reason) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        context.setVariable("reason", reason);
        String body = templateEngine.process("email/rejection-email", context);
        sendHtmlMessage(student.getEmail(), "Project Rejected: " + project.getTitle(), body);
    }

    // ==================== ResearchProject (legacy / supervisor flow) ====================

    public void sendApprovalEmail(User student, ResearchProject project) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        String process = templateEngine.process("email/approval-email", context);
        sendHtmlMessage(student.getEmail(), "Project Approved!", process);
    }

    public void sendRejectionEmail(User student, ResearchProject project, String reason) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        context.setVariable("reason", reason);
        String process = templateEngine.process("email/rejection-email", context);
        sendHtmlMessage(student.getEmail(), "Project Rejection Notice", process);
    }

    public void sendIncubationEmail(User student, ResearchProject project) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("projectTitle", project.getTitle());
        String process = templateEngine.process("email/incubation-email", context);
        sendHtmlMessage(student.getEmail(), "Project Forwarded to Incubation", process);
    }

    // ==================== Internal ====================

    private void sendHtmlMessage(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("[IRIR] " + subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            logger.debug("Email sent to {} — subject: {}", to, subject);
        } catch (MessagingException e) {
            logger.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
