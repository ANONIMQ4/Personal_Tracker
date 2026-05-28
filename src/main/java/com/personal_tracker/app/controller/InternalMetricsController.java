package com.personal_tracker.app.controller;

import com.personal_tracker.app.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class InternalMetricsController {

    private final UserRepository userRepository;

    public InternalMetricsController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/internal/metrics/users")
    public UsersMetrics usersMetrics() {
        long usersCount = userRepository.count();
        return new UsersMetrics(
                usersCount,
                Instant.now().toString()
        );
    }

    public record UsersMetrics(
            long activeUsers,
            String measuredAt
    ) {
    }
}
