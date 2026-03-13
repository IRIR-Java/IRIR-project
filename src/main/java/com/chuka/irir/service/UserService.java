package com.chuka.irir.service;

import com.chuka.irir.dto.UserRegistrationDto;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.AuditLog;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.AuditLogRepository;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service layer for user management operations.
 *
 * Handles user registration, profile updates, role assignment,
 * and account management. All password operations use BCrypt hashing.
 *
 * <p>Integrates with {@link AuditLogRepository} to record significant
 * user-related actions for the admin audit trail.</p>
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       AuditLogRepository auditLogRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== Registration ====================

    /**
     * Registers a new user with the STUDENT role.
     *
     * @param dto the registration form data (validated by controller)
     * @return the newly created User entity
     * @throws IllegalArgumentException if email or studentId is already taken
     */
    public User registerStudent(UserRegistrationDto dto) {
        validateRegistration(dto);

        User user = User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .studentId(dto.getStudentId())
                .password(passwordEncoder.encode(dto.getPassword())) // BCrypt hash
                .roles(new HashSet<>(Set.of(Role.STUDENT)))
                .enabled(true)
                .accountNonLocked(true)
                .build();

        User savedUser = userRepository.save(user);

        // Record audit event
        logAudit(savedUser, "USER_REGISTERED", "New student registered: " + savedUser.getEmail());

        logger.info("New student registered: {} ({})", savedUser.getFullName(), savedUser.getEmail());
        return savedUser;
    }

    // ==================== Queries ====================

    /**
     * Finds a user by ID.
     *
     * @param id the user ID
     * @return the User entity
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Finds a user by email address.
     *
     * @param email the email address
     * @return an Optional containing the User, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Finds all users with a specific role.
     *
     * @param role the role to filter by
     * @return list of users with the given role
     */
    @Transactional(readOnly = true)
    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    /**
     * Returns all users in the system. Used by admin panel.
     *
     * @return list of all users
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // ==================== User Management (Admin) ====================

    /**
     * Updates a user's roles. Only accessible by ADMIN users.
     *
     * @param userId the ID of the user to update
     * @param roles  the new set of roles
     * @param admin  the admin user performing the action
     * @return the updated User entity
     */
    public User updateUserRoles(Long userId, Set<Role> roles, User admin) {
        User user = findById(userId);
        Set<Role> oldRoles = new HashSet<>(user.getRoles());
        user.setRoles(roles);
        User updatedUser = userRepository.save(user);

        logAudit(admin, "USER_ROLE_CHANGED",
                String.format("Roles changed for %s: %s → %s", user.getEmail(), oldRoles, roles));

        logger.info("Admin {} changed roles for {}: {} → {}", admin.getEmail(), user.getEmail(), oldRoles, roles);
        return updatedUser;
    }

    /**
     * Enables or disables a user account. Only accessible by ADMIN users.
     *
     * @param userId  the ID of the user to update
     * @param enabled true to enable, false to disable
     * @param admin   the admin user performing the action
     * @return the updated User entity
     */
    public User setAccountEnabled(Long userId, boolean enabled, User admin) {
        User user = findById(userId);
        user.setEnabled(enabled);
        User updatedUser = userRepository.save(user);

        String action = enabled ? "USER_ENABLED" : "USER_DISABLED";
        logAudit(admin, action, "Account " + action.toLowerCase().replace("user_", "") + ": " + user.getEmail());

        logger.info("Admin {} {} account: {}", admin.getEmail(), action, user.getEmail());
        return updatedUser;
    }

    // ==================== Validation Helpers ====================

    /**
     * Validates registration data before creating a new user.
     *
     * @param dto the registration form data
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRegistration(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email address is already registered: " + dto.getEmail());
        }

        if (dto.getStudentId() != null && !dto.getStudentId().isBlank()
                && userRepository.existsByStudentId(dto.getStudentId())) {
            throw new IllegalArgumentException("Student ID is already registered: " + dto.getStudentId());
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
    }

    // ==================== Audit Logging ====================

    /**
     * Records an audit log entry for a user action.
     *
     * @param user    the user who performed the action
     * @param action  the action type (e.g., "USER_REGISTERED")
     * @param details additional context about the action
     */
    private void logAudit(User user, String action, String details) {
        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .details(details)
                .entityType("User")
                .entityId(user.getId())
                .build();
        auditLogRepository.save(log);
    }
}
