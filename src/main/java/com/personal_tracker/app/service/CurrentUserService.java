package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    public static final String USER_ID_SESSION_KEY = "userId";

    private final UserService userService;

    public CurrentUserService(UserService userService) {
        this.userService = userService;
    }

    public Optional<User> get(HttpSession session) {
        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (!(userId instanceof Long id)) {
            return Optional.empty();
        }
        return userService.getUser(id);
    }
}
