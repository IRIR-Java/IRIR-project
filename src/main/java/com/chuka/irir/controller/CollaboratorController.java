package com.chuka.irir.controller;

import com.chuka.irir.dto.CollaboratorDTO;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.CollaborationRequest;
import com.chuka.irir.model.CollaborationStatus;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.CollaborationRequestService;
import com.chuka.irir.service.CollaboratorRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for the Research Collaborator Recommendation feature (UC-04).
 *
 * <p>Provides endpoints for viewing recommended collaborators and sending
 * collaboration requests to other students with overlapping research interests.</p>
 */
@Controller
@RequestMapping("/student/collaborators")
public class CollaboratorController {

    private static final Logger logger = LoggerFactory.getLogger(CollaboratorController.class);

    private final CollaboratorRecommendationService recommendationService;
    private final CollaborationRequestService collaborationRequestService;
    private final UserRepository userRepository;

    public CollaboratorController(CollaboratorRecommendationService recommendationService,
                                   CollaborationRequestService collaborationRequestService,
                                   UserRepository userRepository) {
        this.recommendationService = recommendationService;
        this.collaborationRequestService = collaborationRequestService;
        this.userRepository = userRepository;
    }

    // ==================== Recommendations Page ====================

    /**
     * GET /student/collaborators — Shows recommended collaborators based on
     * the current student's research keywords and project metadata.
     */
    @GetMapping
    public String showCollaborators(Authentication authentication, Model model) {
        User user = getUser(authentication);
        List<CollaboratorDTO> recommendations = recommendationService.getRecommendations(user.getId());

        // Also load pending incoming requests for the badge count
        List<CollaborationRequest> pendingRequests =
                collaborationRequestService.getPendingRequestsForUser(user);
        List<CollaborationRequest> sentRequests =
                collaborationRequestService.getSentRequests(user);
        List<CollaborationRequest> receivedRequests =
                collaborationRequestService.getReceivedRequests(user);

        Set<Long> pendingSentUserIds = sentRequests.stream()
                .filter(request -> request.getStatus() == CollaborationStatus.PENDING)
                .map(request -> request.getReceiver().getId())
                .collect(Collectors.toSet());

        Set<Long> incomingRequestUserIds = pendingRequests.stream()
                .map(request -> request.getSender().getId())
                .collect(Collectors.toSet());

        Set<Long> connectedUserIds = sentRequests.stream()
                .filter(request -> request.getStatus() == CollaborationStatus.ACCEPTED)
                .map(request -> request.getReceiver().getId())
                .collect(Collectors.toSet());
        connectedUserIds.addAll(
                receivedRequests.stream()
                        .filter(request -> request.getStatus() == CollaborationStatus.ACCEPTED)
                        .map(request -> request.getSender().getId())
                        .collect(Collectors.toSet())
        );

        model.addAttribute("user", user);
        model.addAttribute("collaborators", recommendations);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("sentRequests", sentRequests);
        model.addAttribute("pendingSentUserIds", pendingSentUserIds);
        model.addAttribute("incomingRequestUserIds", incomingRequestUserIds);
        model.addAttribute("connectedUserIds", connectedUserIds);
        model.addAttribute("pageTitle", "Research Collaborators");
        return "student/collaborators";
    }

    // ==================== Send Collaboration Request ====================

    /**
     * POST /student/collaborators/request/{targetUserId} — Sends a collaboration
     * request to the target student and triggers an email notification.
     */
    @PostMapping("/request/{targetUserId}")
    public String sendCollaborationRequest(@PathVariable("targetUserId") Long targetUserId,
                                            Authentication authentication,
                                            RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);

        try {
            collaborationRequestService.sendRequest(user.getId(), targetUserId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Collaboration request sent successfully! The student will be notified by email.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to send collaboration request from {} to {}: {}",
                    user.getId(), targetUserId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to send collaboration request. Please try again.");
        }

        return "redirect:/student/collaborators";
    }

    // ==================== Respond to Request ====================

    /**
     * POST /student/collaborators/respond/{requestId} — Accepts or declines
     * a collaboration request and notifies the sender.
     */
    @PostMapping("/respond/{requestId}")
    public String respondToRequest(@PathVariable("requestId") Long requestId,
                                    @RequestParam("response") String response,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        User user = getUser(authentication);

        try {
            collaborationRequestService.respondToRequest(requestId, response, user.getId());
            String label = response.equalsIgnoreCase("ACCEPTED") ? "accepted" : "declined";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Collaboration request " + label + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to respond to collaboration request [id={}]: {}",
                    requestId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to process your response. Please try again.");
        }

        return "redirect:/student/collaborators";
    }

    // ==================== Helper ====================

    private User getUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
