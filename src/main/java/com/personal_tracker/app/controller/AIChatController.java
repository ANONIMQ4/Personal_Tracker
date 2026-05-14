package com.personal_tracker.app.controller;

import com.personal_tracker.app.model.User;
import com.personal_tracker.app.service.AIChatService;
import com.personal_tracker.app.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AIChatController {

    private static final String USER_ID_SESSION_KEY = "userId";

    private final AIChatService aiChatService;
    private final UserService userService;

    public AIChatController(AIChatService aiChatService, UserService userService) {
        this.aiChatService = aiChatService;
        this.userService = userService;
    }

    @GetMapping("/ai/prompts")
    public ResponseEntity<List<AIChatService.PromptOption>> getPrompts(HttpSession session) {
        return getCurrentUser(session)
                .map(user -> ResponseEntity.ok(aiChatService.getPromptOptions()))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/ai/chat")
    public ResponseEntity<AIChatService.ChatResponse> chat(
            @RequestBody AIChatService.ChatRequest request,
            HttpSession session
    ) {
        return getCurrentUser(session)
                .map(user -> {
                    try {
                        return ResponseEntity.ok(aiChatService.chat(user, request));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().<AIChatService.ChatResponse>build();
                    } catch (IllegalStateException exception) {
                        return ResponseEntity.status(503).<AIChatService.ChatResponse>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private java.util.Optional<User> getCurrentUser(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (!(userId instanceof Long id)) {
            return java.util.Optional.empty();
        }
        return userService.getUser(id);
    }
}
