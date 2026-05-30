package com.personal_tracker.app.controller;

import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class AuthController {

    private static final String USER_ID_SESSION_KEY = "userId";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody LoginRequest request, HttpSession session) {
        return userService.authenticate(request.login(), request.password())
                .map(user -> {
                    session.setAttribute(USER_ID_SESSION_KEY, user.getId());
                    return ResponseEntity.ok(user);
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<User> me(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (!(userId instanceof Long id)) {
            return ResponseEntity.status(401).build();
        }

        return userService.getUser(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/account/balance")
    public ResponseEntity<User> updateAccountBalance(@RequestBody BalanceRequest request, HttpSession session) {
        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (!(userId instanceof Long id)) {
            return ResponseEntity.status(401).build();
        }

        try {
            return ResponseEntity.ok(userService.updateAccountBalance(id, request.accountBalance()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record LoginRequest(String login, String password) {
    }

    public record BalanceRequest(BigDecimal accountBalance) {
    }
}
