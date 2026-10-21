package com.campusrate.service;

import com.campusrate.dto.AuthDtos.AuthResponse;
import com.campusrate.dto.AuthDtos.LoginRequest;
import com.campusrate.dto.AuthDtos.ProfileRequest;
import com.campusrate.dto.AuthDtos.RegisterRequest;
import com.campusrate.dto.AuthDtos.UserResponse;
import com.campusrate.exception.ApiException;
import com.campusrate.model.User;
import com.campusrate.repository.UserRepository;
import com.campusrate.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final List<String> allowedEmailDomains;
    private final Set<String> adminEmails;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.allowed-email-domains}") List<String> allowedEmailDomains,
                       @Value("${app.admin-emails:}") List<String> adminEmails) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.allowedEmailDomains = allowedEmailDomains;
        this.adminEmails = adminEmails.stream()
                .map(AuthService::normalize)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Honeypot: real people never see or fill this hidden field.
        if (request.website() != null && !request.website().isBlank()) {
            throw ApiException.badRequest("Sign up failed. Please try again.");
        }

        String email = normalize(request.email());

        if (!isStudentEmail(email)) {
            throw ApiException.badRequest("Please sign up with your university email address (for example, one ending in .edu).");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists. Try logging in.");
        }

        String hash = passwordEncoder.encode(request.password());
        User user = new User(email, hash, request.displayName().trim());
        applyAdminRole(user);
        user = userRepository.save(user);
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Same message for "no such email" and "wrong password", so attackers can't
        // use the login form to find out which emails have accounts.
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Wrong email or password."));
        applyAdminRole(user); // picks up a newly configured admin on their next login
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, ProfileRequest request) {
        User user = findUser(userId);
        String country = request.homeCountry() == null || request.homeCountry().isBlank()
                ? null : request.homeCountry().trim();
        user.updateProfile(request.displayName().trim(), request.studentStatus(), request.international(),
                country, request.showCountry() && country != null);
        return UserResponse.from(user);
    }

    /** Loads the logged-in user, or fails if their account no longer exists. */
    public User findUser(Long userId) {
        if (userId == null) {
            throw ApiException.unauthorized("Please log in.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Your account was not found. Please log in again."));
    }

    /** Loads the user and checks they're an admin. */
    public User requireAdmin(Long userId) {
        User user = findUser(userId);
        if (!user.isAdmin()) {
            throw ApiException.forbidden("Admins only.");
        }
        return user;
    }

    boolean isStudentEmail(String email) {
        return allowedEmailDomains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .anyMatch(email::endsWith);
    }

    private void applyAdminRole(User user) {
        if (adminEmails.contains(user.getEmail())) {
            user.setRole(User.ROLE_ADMIN);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }
}
