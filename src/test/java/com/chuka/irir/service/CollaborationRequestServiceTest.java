package com.chuka.irir.service;

import com.chuka.irir.model.CollaborationRequest;
import com.chuka.irir.model.CollaborationStatus;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.CollaborationRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationRequestServiceTest {

    @Mock
    private CollaborationRequestRepository requestRepository;

    @Mock
    private UserService userService;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private CollaborationRequestService collaborationRequestService;

    @Test
    void respondToRequest_rejectsUserWhoIsNotTheReceiver() {
        User sender = User.builder()
                .id(1L)
                .email("sender@chuka.ac.ke")
                .password("encoded")
                .firstName("Sender")
                .lastName("User")
                .role(Role.STUDENT)
                .build();

        User receiver = User.builder()
                .id(2L)
                .email("receiver@chuka.ac.ke")
                .password("encoded")
                .firstName("Receiver")
                .lastName("User")
                .role(Role.STUDENT)
                .build();

        CollaborationRequest request = CollaborationRequest.builder()
                .requestId(30L)
                .sender(sender)
                .receiver(receiver)
                .status(CollaborationStatus.PENDING)
                .build();

        when(requestRepository.findByIdWithUsers(30L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> collaborationRequestService.respondToRequest(30L, "ACCEPTED", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You are not allowed to respond to this collaboration request.");

        verify(requestRepository, never()).save(request);
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }
}
