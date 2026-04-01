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

@Repository
public interface CollaborationRequestRepository extends JpaRepository<CollaborationRequest, Long> {

    List<CollaborationRequest> findBySenderOrderByCreatedAtDesc(User sender);

    List<CollaborationRequest> findByReceiverOrderByCreatedAtDesc(User receiver);

    List<CollaborationRequest> findByStatus(CollaborationStatus status);

    boolean existsBySenderAndReceiver(User sender, User receiver);

    List<CollaborationRequest> findByReceiverAndStatus(User receiver, CollaborationStatus status);

    @Query("SELECT r FROM CollaborationRequest r JOIN FETCH r.receiver WHERE r.sender = :sender")
    List<CollaborationRequest> findBySenderWithReceiver(@Param("sender") User sender);

    @Query("SELECT r FROM CollaborationRequest r JOIN FETCH r.sender WHERE r.receiver = :receiver")
    List<CollaborationRequest> findByReceiverWithSender(@Param("receiver") User receiver);

    @Query("SELECT r FROM CollaborationRequest r JOIN FETCH r.sender JOIN FETCH r.receiver WHERE r.requestId = :id")
    Optional<CollaborationRequest> findByIdWithUsers(@Param("id") Long id);
}
