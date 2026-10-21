package com.campusrate.service;

import com.campusrate.dto.AuthDtos.AuthResponse;
import com.campusrate.dto.AuthDtos.LoginRequest;
import com.campusrate.dto.AuthDtos.RegisterRequest;
import com.campusrate.exception.ApiException;
import com.campusrate.model.User;
import com.campusrate.repository.UserRepository;
import com.campusrate.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        JwtService jwtService = new JwtService("test-secret-test-secret-test-secret-123", 60);
        authService = new AuthService(userRepository, new BCryptPasswordEncoder(), jwtService,
                List.of(".edu", ".ac.uk", ".edu.bd"), List.of("boss@emich.edu"));
    }

    @Test
    void acceptsStudentEmailDomains() {
        assertThat(authService.isStudentEmail("arabi@emich.edu")).isTrue();
        assertThat(authService.isStudentEmail("someone@ox.ac.uk")).isTrue();
        assertThat(authService.isStudentEmail("student@bracu.edu.bd")).isTrue();
        assertThat(authService.isStudentEmail("student@bracu.ac.bd")).isFalse(); // .ac.bd isn't in this test's list
        assertThat(authService.isStudentEmail("someone@gmail.com")).isFalse();
    }

    @Test
    void rejectsNonStudentEmailOnSignUp() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("someone@gmail.com", "password123", "Someone", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("university email");
    }

    @Test
    void honeypotFieldBlocksBots() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("bot@emich.edu", "password123", "Bot", "http://spam.example")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void configuredAdminEmailGetsAdminRole() {
        when(userRepository.existsByEmail("boss@emich.edu")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        AuthResponse response = authService.register(new RegisterRequest("Boss@EMICH.edu", "password123", "Boss", ""));

        assertThat(response.user().role()).isEqualTo(User.ROLE_ADMIN);
        assertThat(response.user().email()).isEqualTo("boss@emich.edu"); // emails are stored lowercase
    }

    @Test
    void loginWithUnknownEmailGivesTheSameMessageAsWrongPassword() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@emich.edu", "whatever1")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Wrong email or password.");
    }
}
