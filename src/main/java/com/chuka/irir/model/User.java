package com.chuka.irir.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity representing a user in the IRIR system.
 *
 * Users can hold one or more {@link Role}s (STUDENT, SUPERVISOR, DIRECTORATE, ADMIN).
 * Roles are stored in a separate join table {@code user_roles} using {@link ElementCollection}.
 * Passwords are stored as BCrypt hashes — never in plain text.
 *
 * <p>This entity integrates with Spring Security via {@code CustomUserDetailsService}.</p>
 */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email address — used as the login username. Must be unique. */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** BCrypt-hashed password. Never stored or transmitted in plain text. */
    @NotBlank(message = "Password is required")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50)
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50)
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /** University student/staff ID (e.g., "CS/401/001/2023"). Nullable for admin users. */
    @Column(name = "student_id", unique = true, length = 50)
    private String studentId;

    /** Department — defaults to Computer Science for this system. */
    @Column(length = 100)
    @Builder.Default
    private String department = "Computer Science";

    /** Whether the account is active. Disabled accounts cannot log in. */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Whether the account is locked (e.g., due to too many failed login attempts). */
    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    /**
     * Set of roles assigned to this user.
     * Stored in a separate {@code user_roles} table with columns: user_id, role.
     * Fetched eagerly since roles are needed for every authentication check.
     */
    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /** Timestamp when the account was created. Set automatically on persist. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp of the last profile update. Updated automatically. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "research_interests", length = 500)
    private String researchInterests;

    // ==================== Lifecycle Callbacks ====================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    /** Returns the user's full name (first + last). */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** Checks if the user holds a specific role. */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
