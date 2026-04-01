package com.chuka.irir.repository;

import com.chuka.irir.model.CollaborationRequest;
import com.chuka.irir.model.CollaborationStatus;
import com.chuka.irir.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CollaborationRequest} entities.
 */
@Repository
public interface CollaborationRequestRepository extends JpaRepository<CollaborationRequest, Long> {

    /** Check if a request already exists between sender and receiver. */
    boolean existsBySenderAndReceiver(User sender, User receiver);

    /** Find all requests sent by a user. */
    @Query("SELECT cr FROM CollaborationRequest cr JOIN FETCH cr.receiver WHERE cr.sender = :user ORDER BY cr.createdAt DESC")
    List<CollaborationRequest> findBySenderWithReceiver(@Param("user") User user);

    /** Find all requests received by a user. */
    @Query("SELECT cr FROM CollaborationRequest cr JOIN FETCH cr.sender WHERE cr.receiver = :user ORDER BY cr.createdAt DESC")
    List<CollaborationRequest> findByReceiverWithSender(@Param("user") User user);

    /** Find pending requests received by a user. */
    @Query("SELECT cr FROM CollaborationRequest cr JOIN FETCH cr.sender WHERE cr.receiver = :user AND cr.status = :status ORDER BY cr.createdAt DESC")
    List<CollaborationRequest> findByReceiverAndStatus(@Param("user") User user, @Param("status") CollaborationStatus status);

    /** Find a request by ID with both sender and receiver fetched. */
    @Query("SELECT cr FROM CollaborationRequest cr JOIN FETCH cr.sender JOIN FETCH cr.receiver WHERE cr.id = :id")
    Optional<CollaborationRequest> findByIdWithUsers(@Param("id") Long id);
}
