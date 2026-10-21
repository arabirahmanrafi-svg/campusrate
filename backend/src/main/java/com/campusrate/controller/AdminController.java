package com.campusrate.controller;

import com.campusrate.dto.AdminDtos.AdminStats;
import com.campusrate.dto.AdminDtos.FlaggedReview;
import com.campusrate.dto.AdminDtos.PendingItems;
import com.campusrate.model.Review;
import com.campusrate.service.AdminService;
import com.campusrate.service.AuthService;
import com.campusrate.service.FollowService;
import com.campusrate.service.InternationalService;
import com.campusrate.service.PhotoService;
import com.campusrate.service.PlaceService;
import com.campusrate.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Moderation and the admin dashboard. SecurityConfig only lets tokens with the ADMIN role
 * reach /api/admin/**; each method also re-checks the role in the database, in case the
 * role was taken away after the token was issued.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;
    private final ReviewService reviewService;
    private final PhotoService photoService;
    private final PlaceService placeService;
    private final InternationalService internationalService;
    private final FollowService followService;

    public AdminController(AuthService authService, AdminService adminService, ReviewService reviewService,
                           PhotoService photoService, PlaceService placeService,
                           InternationalService internationalService, FollowService followService) {
        this.authService = authService;
        this.adminService = adminService;
        this.reviewService = reviewService;
        this.photoService = photoService;
        this.placeService = placeService;
        this.internationalService = internationalService;
        this.followService = followService;
    }

    @GetMapping("/stats")
    public AdminStats stats(@AuthenticationPrincipal Long userId) {
        authService.requireAdmin(userId);
        return adminService.stats();
    }

    @GetMapping("/flags")
    public List<FlaggedReview> flaggedReviews(@AuthenticationPrincipal Long userId) {
        authService.requireAdmin(userId);
        return adminService.flaggedReviews();
    }

    @GetMapping("/pending")
    public PendingItems pending(@AuthenticationPrincipal Long userId) {
        authService.requireAdmin(userId);
        return adminService.pending();
    }

    /** The review is genuine: publish it. */
    @PostMapping("/reviews/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveReview(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        reviewService.setStatusByModerator(id, Review.PUBLISHED);
    }

    /** The review is fake or breaks the rules: hide it for good. */
    @PostMapping("/reviews/{id}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReview(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        reviewService.setStatusByModerator(id, Review.REMOVED);
    }

    @PostMapping("/photos/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approvePhoto(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        photoService.approve(id);
    }

    @PostMapping("/photos/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectPhoto(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        photoService.reject(id);
    }

    @PostMapping("/places/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approvePlace(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        placeService.setStatus(id, "APPROVED");
    }

    @PostMapping("/places/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectPlace(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        placeService.setStatus(id, "REJECTED");
    }

    @PostMapping("/links/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveLink(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        internationalService.setLinkStatus(id, "APPROVED");
    }

    @PostMapping("/links/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectLink(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        authService.requireAdmin(userId);
        internationalService.setLinkStatus(id, "REJECTED");
    }

    /** Sends the weekly digest now, instead of waiting for Monday. Handy for demos. */
    @PostMapping("/digest")
    public Map<String, Integer> sendDigest(@AuthenticationPrincipal Long userId) {
        authService.requireAdmin(userId);
        return Map.of("sent", followService.sendWeeklyDigest());
    }
}
