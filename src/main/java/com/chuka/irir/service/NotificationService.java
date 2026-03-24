package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import com.chuka.irir.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class NotificationService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

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

    private void sendHtmlMessage(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }
}
