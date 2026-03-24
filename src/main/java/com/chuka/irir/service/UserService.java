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

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public User registerStudent(UserRegistrationDto dto) {
        validateRegistration(dto);

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setRegNumber(dto.getStudentId());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.STUDENT);
        user.setEnabled(true);
        user.setAccountNonLocked(true);

        User saved = userRepository.save(user);
        logAudit(saved, "USER_REGISTERED", "New student registered: " + saved.getEmail());
        logger.info("New student registered: {} ({})", saved.getFullName(), saved.getEmail());
        return saved;
    }

    // ==================== Queries ====================

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // ==================== Admin operations ====================

    public User updateUserRole(Long userId, Role role, User admin) {
        User user = findById(userId);
        user.setRole(role);
        User updated = userRepository.save(user);
        logAudit(admin, "USER_ROLE_CHANGED", "Role changed for " + user.getEmail() + " -> " + role);
        logger.info("Admin {} changed role for {} to {}", admin.getEmail(), user.getEmail(), role);
        return updated;
    }

    public User setAccountEnabled(Long userId, boolean enabled, User admin) {
        User user = findById(userId);
        user.setEnabled(enabled);
        User updated = userRepository.save(user);
        String action = enabled ? "USER_ENABLED" : "USER_DISABLED";
        logAudit(admin, action, "Account " + action + " for: " + user.getEmail());
        logger.info("Admin {} {} account for {}", admin.getEmail(), action, user.getEmail());
        return updated;
    }

    // ==================== Validation helpers ====================

    private void validateRegistration(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + dto.getEmail());
        }

        if (dto.getStudentId() != null && !dto.getStudentId().isBlank()
                && userRepository.existsByRegNumber(dto.getStudentId())) {
            throw new IllegalArgumentException("Registration number already exists: " + dto.getStudentId());
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
    }

    // ==================== Audit logging ====================

    private void logAudit(User user, String action, String details) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setDetails(details);
        log.setEntityType("User");
        log.setEntityId(user.getId());
        auditLogRepository.save(log);
    }
}