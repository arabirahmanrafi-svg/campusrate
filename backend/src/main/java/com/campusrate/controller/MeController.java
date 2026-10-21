package com.campusrate.controller;

import com.campusrate.dto.AuthDtos.ProfileRequest;
import com.campusrate.dto.AuthDtos.UserResponse;
import com.campusrate.dto.CommunityDtos.NotificationResponse;
import com.campusrate.service.AuthService;
import com.campusrate.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The logged-in student's own profile and notifications. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record NotificationsResponse(int unread, List<NotificationResponse> items) {
    }

    private final AuthService authService;
    private final NotificationService notificationService;

    public MeController(AuthService authService, NotificationService notificationService) {
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @GetMapping("/profile")
    public UserResponse profile(@AuthenticationPrincipal Long userId) {
        return authService.currentUser(userId);
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal Long userId, @Valid @RequestBody ProfileRequest request) {
        return authService.updateProfile(userId, request);
    }

    @GetMapping("/notifications")
    public NotificationsResponse notifications(@AuthenticationPrincipal Long userId) {
        Long id = authService.findUser(userId).getId();
        return new NotificationsResponse(notificationService.unreadCount(id), notificationService.recent(id));
    }

    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(authService.findUser(userId).getId());
    }
}
