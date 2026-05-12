package com.personal_tracker.app.controller;

import com.personal_tracker.app.model.BrokerHolding;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.service.BrokerHoldingService;
import com.personal_tracker.app.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
public class BrokerHoldingController {

    private static final String USER_ID_SESSION_KEY = "userId";

    private final BrokerHoldingService brokerHoldingService;
    private final UserService userService;

    public BrokerHoldingController(BrokerHoldingService brokerHoldingService, UserService userService) {
        this.brokerHoldingService = brokerHoldingService;
        this.userService = userService;
    }

    @GetMapping("/broker/holdings")
    public ResponseEntity<List<BrokerHolding>> getHoldings(HttpSession session) {
        return getCurrentUser(session)
                .map(user -> ResponseEntity.ok(brokerHoldingService.getHoldings(user.getId())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/broker/screenshot")
    public ResponseEntity<BrokerImportResult> uploadBrokerScreenshot(
            @RequestParam("screenshot") MultipartFile screenshot,
            HttpSession session
    ) {
        return getCurrentUser(session)
                .map(user -> {
                    try {
                        BrokerHoldingService.ImportResult result = brokerHoldingService.importScreenshot(user, screenshot);
                        return ResponseEntity.ok(new BrokerImportResult(result.importedCount()));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new BrokerImportResult(0));
                    } catch (IOException | InterruptedException | IllegalStateException exception) {
                        return ResponseEntity.status(503).body(new BrokerImportResult(0));
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

    public record BrokerImportResult(int importedCount) {
    }
}
