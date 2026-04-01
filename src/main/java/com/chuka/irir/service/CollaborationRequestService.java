package com.chuka.irir.service;

import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.CollaborationRequest;
import com.chuka.irir.model.CollaborationStatus;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.CollaborationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing collaboration requests between students (UC-04).
 *
 * <p>Handles sending, responding to, and querying collaboration requests.
 * Sends email notifications when a request is sent or responded to.</p>
 */
@Service
@Transactional
public class CollaborationRequestService {

    private static final Logger logger = LoggerFactory.getLogger(CollaborationRequestService.class);

    private final CollaborationRequestRepository requestRepository;
    private final UserService userService;
    private final JavaMailSender mailSender;

    public CollaborationRequestService(CollaborationRequestRepository requestRepository,
                                        UserService userService,
                                        JavaMailSender mailSender) {
        this.requestRepository = requestRepository;
        this.userService = userService;
        this.mailSender = mailSender;
    }

    // ==================== Send Request ====================

    /**
     * Sends a collaboration request from one student to another.
     * Also sends an email notification to the receiver.
     *
     * @param senderId   the ID of the student sending the request
     * @param receiverId the ID of the target student
     * @return the created CollaborationRequest entity
     * @throws IllegalArgumentException if a request already exists or sender == receiver
     */
    public CollaborationRequest sendRequest(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot send a collaboration request to yourself.");
        }

        User sender = userService.findById(senderId);
        User receiver = userService.findById(receiverId);

        // Check for duplicate request
        if (requestRepository.existsBySenderAndReceiver(sender, receiver)) {
            throw new IllegalArgumentException(
                    "You have already sent a collaboration request to " + receiver.getFullName() + ".");
        }

        // Also check the reverse direction
        if (requestRepository.existsBySenderAndReceiver(receiver, sender)) {
            throw new IllegalArgumentException(
                    receiver.getFullName() + " has already sent you a collaboration request.");
        }

        CollaborationRequest request = CollaborationRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .status(CollaborationStatus.PENDING)
                .build();

        CollaborationRequest saved = requestRepository.save(request);

        // Send email notification
        sendNotificationEmail(sender, receiver,
                "New Collaboration Request from " + sender.getFullName(),
                String.format(
                        "Hello %s,\n\n%s (%s) would like to collaborate with you on research.\n\n" +
                        "You can respond to this request by logging into IRIR.\n\n" +
                        "Best regards,\nIRIR System",
                        receiver.getFirstName(), sender.getFullName(), sender.getEmail()));

        logger.info("Collaboration request sent from {} to {}", sender.getEmail(), receiver.getEmail());
        return saved;
    }

    // ==================== Respond to Request ====================

    /**
     * Responds to a collaboration request (ACCEPTED or DECLINED).
     * Sends an email notification to the original sender.
     *
     * @param requestId the ID of the collaboration request
     * @param response  "ACCEPTED" or "DECLINED"
     * @return the updated CollaborationRequest
     * @throws ResourceNotFoundException if the request doesn't exist
     * @throws IllegalArgumentException  if the response is invalid
     */
    public CollaborationRequest respondToRequest(Long requestId, String response, Long receiverId) {
        CollaborationRequest request = requestRepository.findByIdWithUsers(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("CollaborationRequest", "id", requestId));

        if (!request.getReceiver().getId().equals(receiverId)) {
            throw new IllegalArgumentException("You are not allowed to respond to this collaboration request.");
        }

        if (request.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalArgumentException("This request has already been " +
                    request.getStatus().name().toLowerCase() + ".");
        }

        CollaborationStatus newStatus;
        try {
            newStatus = CollaborationStatus.valueOf(response.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid response. Must be ACCEPTED or DECLINED.");
        }

        if (newStatus == CollaborationStatus.PENDING) {
            throw new IllegalArgumentException("Invalid response. Must be ACCEPTED or DECLINED.");
        }

        request.setStatus(newStatus);
        request.setRespondedAt(LocalDateTime.now());
        CollaborationRequest saved = requestRepository.save(request);

        // Notify the original sender
        User sender = request.getSender();
        User receiver = request.getReceiver();
        String statusLabel = newStatus == CollaborationStatus.ACCEPTED ? "accepted" : "declined";

        sendNotificationEmail(receiver, sender,
                "Collaboration Request " + statusLabel.substring(0, 1).toUpperCase() + statusLabel.substring(1),
                String.format(
                        "Hello %s,\n\n%s has %s your collaboration request.\n\n%s\n\n" +
                        "Best regards,\nIRIR System",
                        sender.getFirstName(),
                        receiver.getFullName(),
                        statusLabel,
                        newStatus == CollaborationStatus.ACCEPTED
                                ? "You can now reach them at: " + receiver.getEmail()
                                : ""));

        logger.info("Collaboration request [id={}] {} by {}", requestId, statusLabel, receiver.getEmail());
        return saved;
    }

    // ==================== Queries ====================

    /** Returns pending collaboration requests received by the given user. */
    @Transactional(readOnly = true)
    public List<CollaborationRequest> getPendingRequestsForUser(User user) {
        return requestRepository.findByReceiverAndStatus(user, CollaborationStatus.PENDING);
    }

    /** Returns all collaboration requests sent by the given user. */
    @Transactional(readOnly = true)
    public List<CollaborationRequest> getSentRequests(User user) {
        return requestRepository.findBySenderWithReceiver(user);
    }

    /** Returns all collaboration requests received by the given user. */
    @Transactional(readOnly = true)
    public List<CollaborationRequest> getReceivedRequests(User user) {
        return requestRepository.findByReceiverWithSender(user);
    }

    // ==================== Email Notification ====================

    /**
     * Sends an email notification. Failures are logged but do not propagate.
     */
    private void sendNotificationEmail(User from, User to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to.getEmail());
            message.setSubject("[IRIR] " + subject);
            message.setText(body);
            message.setReplyTo(from.getEmail());
            mailSender.send(message);
            logger.debug("Email sent to {} — subject: {}", to.getEmail(), subject);
        } catch (Exception e) {
            // Don't fail the request if email sending fails
            logger.warn("Failed to send email notification to {}: {}", to.getEmail(), e.getMessage());
        }
    }
}
