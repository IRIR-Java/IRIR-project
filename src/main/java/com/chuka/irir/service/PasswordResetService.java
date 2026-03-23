package com.chuka.rir.service;

import com.chuka.rir.model.PasswordResetToken;
import com.chuka.rir.model.User;
import com.chuka.rir.repository.PasswordResetTokenRepository;
import com.chuka.rir.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Full flow: validate email → delete old tokens → generate new token → send email.
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with email: " + email));

        // Delete any existing unused tokens for this user
        tokenRepository.deleteAllByUserId(user.getId());

        // Generate and save new token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        tokenRepository.save(resetToken);

        // Send email
        sendResetEmail(user, token);
    }

    /**
     * Validates token and updates the user's password.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token."));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        if (resetToken.isUsed()) {
            throw new RuntimeException("This reset link has already been used.");
        }

        // Update password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used (don't delete — keep for audit trail)
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    /**
     * Checks if a token is valid (exists, not expired, not used).
     * Used by the controller to validate before showing the reset form.
     */
    public boolean isTokenValid(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> !t.isExpired() && !t.isUsed())
                .orElse(false);
    }

    /**
     * Sends the password reset email with a clickable link.
     */
    private void sendResetEmail(User user, String token) {
        String resetLink = "http://localhost:8080/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("IRIR — Password Reset Request");
        message.setText(
                "Hello " + user.getFirstName() + ",\n\n" +
                "We received a request to reset your IRIR account password.\n\n" +
                "Click the link below to reset your password:\n" +
                resetLink + "\n\n" +
                "This link expires in 24 hours.\n\n" +
                "If you did not request this, please ignore this email — " +
                "your password will remain unchanged.\n\n" +
                "Regards,\nIRIR Team"
        );

        mailSender.send(message);
    }

    /**
     * Scheduled cleanup: removes expired tokens from DB every 24 hours.
     * Runs automatically — no manual call needed.
     */
    @Scheduled(fixedRate = 86400000) // every 24 hours in milliseconds
    @Transactional
    public void purgeExpiredTokens() {
        tokenRepository.deleteAllExpiredTokens(LocalDateTime.now());
    }
}