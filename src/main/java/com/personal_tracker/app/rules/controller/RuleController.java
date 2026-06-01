package com.personal_tracker.app.rules.controller;

import com.personal_tracker.app.rules.dto.ApplyRuleRequest;
import com.personal_tracker.app.rules.dto.ParsedRuleResponse;
import com.personal_tracker.app.rules.dto.RuleDto;
import com.personal_tracker.app.rules.dto.RulePreviewResponse;
import com.personal_tracker.app.rules.model.RuleDefinition;
import com.personal_tracker.app.rules.service.RuleService;
import com.personal_tracker.app.service.CurrentUserService;
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

@RestController
public class RuleController {

    private final CurrentUserService currentUserService;
    private final RuleService ruleService;

    public RuleController(CurrentUserService currentUserService, RuleService ruleService) {
        this.currentUserService = currentUserService;
        this.ruleService = ruleService;
    }

    @PostMapping("/api/rules/parse")
    public ResponseEntity<?> parse(@RequestBody ParseRuleRequest request, HttpSession session) {
        return currentUserService.get(session)
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
        return currentUserService.get(session)
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
        return currentUserService.get(session)
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
        return currentUserService.get(session)
                .map(user -> ResponseEntity.ok(ruleService.getRules(user)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PatchMapping("/api/rules/{id}/enabled")
    public ResponseEntity<?> setEnabled(
            @PathVariable Long id,
            @RequestBody ToggleRuleRequest request,
            HttpSession session
    ) {
        return currentUserService.get(session)
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
        return currentUserService.get(session)
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

    public record ErrorResponse(String message) {
    }

    public record ParseRuleRequest(String prompt) {
    }

    public record ToggleRuleRequest(boolean enabled) {
    }
}
