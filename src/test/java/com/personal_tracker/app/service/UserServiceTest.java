package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserService userService = new UserService(userRepository, passwordEncoder);

    @Test
    void createUserStoresPasswordHash() {
        when(userRepository.findByUsername("test")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUser(" test ", " TEST@example.com ", "secret123");

        assertThat(user.getUsername()).isEqualTo("test");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPassword()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", user.getPassword())).isTrue();
    }

    @Test
    void authenticateMigratesLegacyPlainTextPassword() {
        User legacyUser = new User("legacy", "legacy@example.com", "plain-password");
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(legacyUser));

        Optional<User> authenticated = userService.authenticate("legacy", "plain-password");

        assertThat(authenticated).contains(legacyUser);
        assertThat(legacyUser.getPassword()).isNotEqualTo("plain-password");
        assertThat(passwordEncoder.matches("plain-password", legacyUser.getPassword())).isTrue();
        verify(userRepository).save(legacyUser);
    }

    @Test
    void authenticateNormalizesEmailLogin() {
        User user = new User("test", "test@example.com", passwordEncoder.encode("secret123"));
        when(userRepository.findByUsername("TEST@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        Optional<User> authenticated = userService.authenticate(" TEST@example.com ", "secret123");

        assertThat(authenticated).contains(user);
    }
}
