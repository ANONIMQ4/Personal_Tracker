package com.personal_tracker.app.controller;

import com.personal_tracker.app.model.User;
import com.personal_tracker.app.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            return ResponseEntity.ok(userService.createUser(request.username(), request.email(), request.password()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    public record CreateUserRequest(String username, String email, String password) {
    }

    public record ErrorResponse(String message) {
    }
}
