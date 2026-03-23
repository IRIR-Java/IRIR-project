package com.chuka.irir.repository;

import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRegNumber(String regNumber);

    Optional<User> findByRegNumber(String regNumber);

    List<User> findByRole(Role role);

    @Query("SELECT u FROM User u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<User> searchByNameOrEmail(@Param("term") String term);

    long countByRole(Role role);
    
    // Additional custom query
    List<User> findByDepartment(String department);
}
