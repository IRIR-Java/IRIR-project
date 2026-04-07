package com.chuka.irir.controller;

import com.chuka.irir.dto.ChatRequest;
import com.chuka.irir.dto.ChatResponse;
import com.chuka.irir.service.NavigationChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/chat")
public class ChatbotController {

    private final NavigationChatService navigationChatService;

    public ChatbotController(NavigationChatService navigationChatService) {
        this.navigationChatService = navigationChatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ChatResponse(
                            "Please sign in to use the navigator.",
                            null,
                            null
                    ));
        }

        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        ChatResponse response = navigationChatService.reply(request, roles);
        return ResponseEntity.ok(response);
    }
}
