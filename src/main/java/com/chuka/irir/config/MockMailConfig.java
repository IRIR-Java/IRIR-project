package com.chuka.irir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;

/**
 * Mocks the JavaMailSender so the application does not crash
 * when trying to connect to a real SMTP server during development.
 */
@Configuration
public class MockMailConfig {

    private static final Logger logger = LoggerFactory.getLogger(MockMailConfig.class);

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        logger.info("Injecting Mock JavaMailSender to prevent SMTP connection errors.");
        return new JavaMailSender() {
            @Override
            public MimeMessage createMimeMessage() {
                // Return a dummy implementation if needed, though most things use SimpleMailMessage or preparers
                return null; 
            }

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
                return null;
            }

            @Override
            public void send(MimeMessage mimeMessage) throws MailException {
                logger.info("MOCK MimeMessage SENT");
            }

            @Override
            public void send(MimeMessage... mimeMessages) throws MailException {
                logger.info("MOCK MimeMessages SENT: count={}", mimeMessages.length);
            }

            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
                logger.info("MOCK MimeMessagePreparator SENT");
            }

            @Override
            public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
                logger.info("MOCK MimeMessagePreparators SENT: count={}", mimeMessagePreparators.length);
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) throws MailException {
                logger.info("MOCK EMAIL SENT. To: {}, Subject: {}",
                        (simpleMessage.getTo() != null && simpleMessage.getTo().length > 0) ? simpleMessage.getTo()[0] : "unknown",
                        simpleMessage.getSubject());
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) throws MailException {
                for (SimpleMailMessage msg : simpleMessages) {
                    send(msg);
                }
            }
        };
    }
}
