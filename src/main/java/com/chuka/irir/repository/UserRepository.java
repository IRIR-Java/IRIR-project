package com.chuka.irir.repository;

import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * Provides CRUD operations plus custom queries for authentication,
 * role-based lookups, and user management.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Find a user by their email address (used as login username). */
    Optional<User> findByEmail(String email);

    /** Check if an email address is already registered. */
    boolean existsByEmail(String email);

    /** Check if a student/staff ID is already registered. */
    boolean existsByStudentId(String studentId);

    /** Find a user by their university student/staff ID. */
    Optional<User> findByStudentId(String studentId);

    /** Find all users who hold a specific role. */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRole(@Param("role") Role role);

    /** Find all users whose names contain the search term (case-insensitive). */
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<User> searchByNameOrEmail(@Param("term") String term);

    /** Count users by role. Used for admin dashboard statistics. */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r = :role")
    long countByRole(@Param("role") Role role);
}
