package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByLogin(String login) {
        String normalizedLogin = login.trim();
        return userRepository.findByUsername(normalizedLogin)
                .or(() -> userRepository.findByEmail(normalizedLogin.toLowerCase()));
    }

    public Optional<User> authenticate(String login, String password) {
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return getUserByLogin(login)
                .filter(user -> passwordMatches(user, password));
    }

    @Transactional
    public User createUser(String username, String email, String password) {
        String normalizedUsername = requireValue(username, "Имя пользователя");
        String normalizedEmail = requireValue(email, "Email").toLowerCase();
        String normalizedPassword = requireValue(password, "Пароль");
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        User user = new User(normalizedUsername, normalizedEmail, passwordEncoder.encode(normalizedPassword));
        return userRepository.save(user);
    }

    @Transactional
    public User updateAccountBalance(Long userId, BigDecimal accountBalance) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        user.setAccountBalance(accountBalance);
        return userRepository.save(user);
    }

    private boolean passwordMatches(User user, String rawPassword) {
        String storedPassword = user.getPassword();
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (looksLikeBcrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        boolean matchesLegacyPassword = storedPassword.equals(rawPassword);
        if (matchesLegacyPassword) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
        }
        return matchesLegacyPassword;
    }

    private boolean looksLikeBcrypt(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " обязателен");
        }
        return value.trim();
    }
}
