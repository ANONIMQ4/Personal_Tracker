package com.personal_tracker.app.service;

import com.personal_tracker.app.model.User;
import com.personal_tracker.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByLogin(String login) {
        return userRepository.findByUsername(login)
                .or(() -> userRepository.findByEmail(login));
    }

    public Optional<User> authenticate(String login, String password) {
        return getUserByLogin(login)
                .filter(user -> user.getPassword().equals(password));
    }

    public User createUser(String username, String email, String password) {
        User user = new User(username, email, password);
        return userRepository.save(user);
    }

    @Transactional
    public User updateAccountBalance(Long userId, BigDecimal accountBalance) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        user.setAccountBalance(accountBalance);
        return userRepository.save(user);
    }
}
