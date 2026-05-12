package com.personal_tracker.app.controller;

import com.personal_tracker.app.model.BrokerHolding;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.service.BrokerHoldingService;
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

    @PostMapping("/broker/holdings")
    public ResponseEntity<BrokerHolding> addHolding(
            @RequestBody BrokerHoldingService.HoldingRequest request,
            HttpSession session
    ) {
        return getCurrentUser(session)
                .map(user -> {
                    try {
                        return ResponseEntity.ok(brokerHoldingService.addHolding(user, request));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().<BrokerHolding>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PatchMapping("/broker/holdings/{holdingId}/quantity")
    public ResponseEntity<BrokerHolding> updateQuantity(
            @PathVariable Long holdingId,
            @RequestBody BrokerHoldingService.QuantityRequest request,
            HttpSession session
    ) {
        return getCurrentUser(session)
                .map(user -> {
                    try {
                        return ResponseEntity.ok(brokerHoldingService.updateQuantity(user.getId(), holdingId, request));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().<BrokerHolding>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @DeleteMapping("/broker/holdings/{holdingId}")
    public ResponseEntity<Void> deleteHolding(@PathVariable Long holdingId, HttpSession session) {
        return getCurrentUser(session)
                .map(user -> {
                    try {
                        brokerHoldingService.deleteHolding(user.getId(), holdingId);
                        return ResponseEntity.noContent().<Void>build();
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.notFound().<Void>build();
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
