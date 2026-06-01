package com.personal_tracker.app.controller;

import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.service.CurrentUserService;
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

    private final CurrentUserService currentUserService;
    private final UserService userService;

    public AuthController(CurrentUserService currentUserService, UserService userService) {
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody LoginRequest request, HttpSession session) {
        return userService.authenticate(request.login(), request.password())
                .map(user -> {
                    session.setAttribute(CurrentUserService.USER_ID_SESSION_KEY, user.getId());
                    return ResponseEntity.ok(user);
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            return ResponseEntity.ok(userService.createUser(request.username(), request.email(), request.password()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<User> me(HttpSession session) {
        return currentUserService.get(session)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/account/balance")
    public ResponseEntity<User> updateAccountBalance(@RequestBody BalanceRequest request, HttpSession session) {
        return currentUserService.get(session)
                .map(user -> {
                    try {
                        return ResponseEntity.ok(userService.updateAccountBalance(user.getId(), request.accountBalance()));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().<User>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    public record LoginRequest(String login, String password) {
    }

    public record CreateUserRequest(String username, String email, String password) {
    }

    public record BalanceRequest(BigDecimal accountBalance) {
    }

    public record ErrorResponse(String message) {
    }
}
