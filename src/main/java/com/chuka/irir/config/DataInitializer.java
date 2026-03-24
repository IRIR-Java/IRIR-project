package com.chuka.irir.config;

import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/**
 * Data initializer that seeds the database with default users on first startup.
 *
 * Creates a default admin account if no admin users exist in the database.
 * This ensures that the system always has at least one administrator who can
 * manage users and assign roles.
 *
 * <p><b>IMPORTANT:</b> Change the default admin password after first login in production!</p>
 */
@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Seeds a default admin user if none exists.
     * Runs automatically on application startup.
     */
    @Bean
    public CommandLineRunner initializeData(UserRepository userRepository,
                                            PasswordEncoder passwordEncoder) {
        return args -> {
            // Only seed if no admin users exist
            if (userRepository.findByRole(Role.ADMIN).isEmpty()) {
                logger.info("No admin users found — creating default admin account...");

                User admin = new User();
                admin.setFirstName("System");
                admin.setLastName("Administrator");
                admin.setFullName("System Administrator");
                admin.setEmail("admin@chuka.ac.ke");
                admin.setPassword(passwordEncoder.encode("Admin@2024"));
                admin.setRegNumber("ADMIN");
                admin.setDepartment("Computer Science");
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);
                admin.setAccountNonLocked(true);

                userRepository.save(admin);
                logger.info("Default admin account created: admin@chuka.ac.ke");
                logger.warn("⚠ CHANGE THE DEFAULT ADMIN PASSWORD AFTER FIRST LOGIN!");
            } else {
                logger.info("Admin account(s) already exist — skipping seed.");
            }

            // Log user statistics on startup
            logger.info("=== IRIR User Statistics ===");
            logger.info("  Students:    {}", userRepository.countByRole(Role.STUDENT));
            logger.info("  Supervisors: {}", userRepository.countByRole(Role.SUPERVISOR));
            logger.info("  Directorate: {}", userRepository.countByRole(Role.DIRECTORATE));
            logger.info("  Admins:      {}", userRepository.countByRole(Role.ADMIN));
            logger.info("  Total:       {}", userRepository.count());
        };
    }
}
