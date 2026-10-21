package com.campusrate.controller;

import com.campusrate.dto.ReviewDtos.ReviewRequest;
import com.campusrate.dto.ReviewDtos.ReviewResponse;
import com.campusrate.dto.ReviewDtos.VoteResponse;
import com.campusrate.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints that change data. All of them require a logged-in user (see SecurityConfig). */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(userId, request);
    }

    @PutMapping("/reviews/{id}")
    public ReviewResponse update(@AuthenticationPrincipal Long userId,
                                 @PathVariable Long id,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(userId, id, request);
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        reviewService.delete(userId, id);
    }

    @PostMapping("/reviews/{id}/helpful")
    public VoteResponse toggleHelpful(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return reviewService.toggleHelpful(userId, id);
    }

    @GetMapping("/me/reviews")
    public List<ReviewResponse> myReviews(@AuthenticationPrincipal Long userId) {
        return reviewService.myReviews(userId);
    }
}
