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

import java.util.HashSet;
import java.util.Set;

/**
 * Data initializer that seeds the database with default users on first startup.
 *
 * Creates:
 * - A default ADMIN user (also has STUDENT role so they can test the student flow)
 * - Two test STUDENT accounts for testing similarity detection
 *
 * <p><b>IMPORTANT:</b> Change the default passwords after first login in production!</p>
 */
@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Seeds default users if they don't exist.
     * Runs automatically on application startup.
     */
    @Bean
    public CommandLineRunner initializeData(UserRepository userRepository,
                                            PasswordEncoder passwordEncoder) {
        return args -> {
            // ---- Seed Admin (with STUDENT role too, so they can test the full flow) ----
            if (userRepository.findByRole(Role.ADMIN).isEmpty()) {
                logger.info("No admin users found — creating default admin account...");

                User admin = User.builder()
                        .firstName("System")
                        .lastName("Administrator")
                        .email("admin@chuka.ac.ke")
                        .password(passwordEncoder.encode("Admin@2024"))
                        .studentId(null)
                        .department("Computer Science")
                        .roles(new HashSet<>(Set.of(Role.ADMIN, Role.STUDENT)))
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();

                userRepository.save(admin);
                logger.info("Default admin account created: admin@chuka.ac.ke (roles: ADMIN, STUDENT)");
                logger.warn("⚠ CHANGE THE DEFAULT ADMIN PASSWORD AFTER FIRST LOGIN!");
            } else {
                logger.info("Admin account(s) already exist — skipping seed.");
            }

            // ---- Seed Test Student 1 ----
            if (userRepository.findByEmail("student1@chuka.ac.ke").isEmpty()) {
                User student1 = User.builder()
                        .firstName("John")
                        .lastName("Mwangi")
                        .email("student1@chuka.ac.ke")
                        .password(passwordEncoder.encode("Student@2024"))
                        .studentId("CS/401/001/2024")
                        .department("Computer Science")
                        .roles(new HashSet<>(Set.of(Role.STUDENT)))
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();
                userRepository.save(student1);
                logger.info("Test student created: student1@chuka.ac.ke");
            }

            // ---- Seed Test Student 2 ----
            if (userRepository.findByEmail("student2@chuka.ac.ke").isEmpty()) {
                User student2 = User.builder()
                        .firstName("Jane")
                        .lastName("Wanjiku")
                        .email("student2@chuka.ac.ke")
                        .password(passwordEncoder.encode("Student@2024"))
                        .studentId("CS/401/002/2024")
                        .department("Computer Science")
                        .roles(new HashSet<>(Set.of(Role.STUDENT)))
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();
                userRepository.save(student2);
                logger.info("Test student created: student2@chuka.ac.ke");
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
