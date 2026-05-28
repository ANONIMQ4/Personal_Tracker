package com.personal_tracker.app.rules.controller;

import com.personal_tracker.app.model.User;
import com.personal_tracker.app.rules.dto.ApplyRuleRequest;
import com.personal_tracker.app.rules.dto.ParseRuleRequest;
import com.personal_tracker.app.rules.dto.ParsedRuleResponse;
import com.personal_tracker.app.rules.dto.RuleDto;
import com.personal_tracker.app.rules.dto.RulePreviewResponse;
import com.personal_tracker.app.rules.dto.ToggleRuleRequest;
import com.personal_tracker.app.rules.model.RuleDefinition;
import com.personal_tracker.app.rules.service.RuleService;
import com.personal_tracker.app.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
public class RuleController {

    private static final String USER_ID_SESSION_KEY = "userId";

    private final RuleService ruleService;
    private final UserService userService;

    public RuleController(RuleService ruleService, UserService userService) {
        this.ruleService = ruleService;
        this.userService = userService;
    }

    @PostMapping("/api/rules/parse")
    public ResponseEntity<?> parse(@RequestBody ParseRuleRequest request, HttpSession session) {
        return currentUser(session)
                .<ResponseEntity<?>>map(user -> {
                    try {
                        ParsedRuleResponse response = ruleService.parse(user, request.prompt());
                        return ResponseEntity.ok(response);
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/api/rules/preview")
    public ResponseEntity<?> preview(@RequestBody RuleDefinition rule, HttpSession session) {
        return currentUser(session)
                .<ResponseEntity<?>>map(user -> {
                    try {
                        RulePreviewResponse response = ruleService.preview(user, rule);
                        return ResponseEntity.ok(response);
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/api/rules/apply")
    public ResponseEntity<?> apply(@RequestBody ApplyRuleRequest request, HttpSession session) {
        return currentUser(session)
                .<ResponseEntity<?>>map(user -> {
                    try {
                        RulePreviewResponse response = ruleService.apply(user, request);
                        return ResponseEntity.ok(response);
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping("/api/rules")
    public ResponseEntity<List<RuleDto>> getRules(HttpSession session) {
        return currentUser(session)
                .map(user -> ResponseEntity.ok(ruleService.getRules(user)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PatchMapping("/api/rules/{id}/enabled")
    public ResponseEntity<?> setEnabled(
            @PathVariable Long id,
            @RequestBody ToggleRuleRequest request,
            HttpSession session
    ) {
        return currentUser(session)
                .<ResponseEntity<?>>map(user -> {
                    try {
                        return ResponseEntity.ok(ruleService.setEnabled(user, id, request.enabled()));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @DeleteMapping("/api/rules/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        return currentUser(session)
                .<ResponseEntity<?>>map(user -> {
                    try {
                        ruleService.delete(user, id);
                        return ResponseEntity.noContent().build();
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private Optional<User> currentUser(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (!(userId instanceof Long id)) {
            return Optional.empty();
        }
        return userService.getUser(id);
    }

    public record ErrorResponse(String message) {
    }
}
