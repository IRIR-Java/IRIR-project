package com.chuka.irir.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Column(unique = true, length = 50)
    private String studentId;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "Password is required")
    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String profilePhotoPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 100)
    @Builder.Default
    private String department = "Computer Science";

    private Integer yearOfStudy;

    @Column(columnDefinition = "TEXT")
    private String researchInterests;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ResearchProject> researchProjects = new ArrayList<>();

    // Spring Security fields
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    // ==================== Convenience Methods ====================

    /** Returns the user's full name by combining first and last name. */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Returns a singleton set of the user's role for Spring Security compatibility.
     * The codebase uses both single-role (role) and multi-role (roles) patterns.
     */
    public Set<Role> getRoles() {
        Set<Role> roles = new HashSet<>();
        if (role != null) {
            roles.add(role);
        }
        return roles;
    }

    /**
     * Sets the user's role from a set. Takes the first role from the set.
     */
    public void setRoles(Set<Role> roles) {
        if (roles != null && !roles.isEmpty()) {
            this.role = roles.iterator().next();
        }
    }
}
