package com.campusrate.controller;

import com.campusrate.dto.CommunityDtos.DiningToday;
import com.campusrate.dto.CommunityDtos.PlaceDetail;
import com.campusrate.dto.CommunityDtos.PlaceRequest;
import com.campusrate.dto.CommunityDtos.PlaceReviewRequest;
import com.campusrate.dto.CommunityDtos.PlaceReviewResponse;
import com.campusrate.dto.CommunityDtos.PlaceSummary;
import com.campusrate.dto.CommunityDtos.PlaceTypeInfo;
import com.campusrate.service.PlaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dorms, dining halls, courses, campus jobs and clubs. */
@RestController
@RequestMapping("/api")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /** The rating questions for each place type; React builds its review forms from this. */
    @GetMapping("/place-types")
    public List<PlaceTypeInfo> types() {
        return placeService.types();
    }

    /** GET /api/universities/1/places?type=DORM */
    @GetMapping("/universities/{id}/places")
    public List<PlaceSummary> list(@PathVariable Long id, @RequestParam String type) {
        return placeService.list(id, type);
    }

    @PostMapping("/universities/{id}/places")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceSummary suggest(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                @Valid @RequestBody PlaceRequest request) {
        return placeService.suggest(userId, id, request);
    }

    @GetMapping("/universities/{id}/dining/today")
    public List<DiningToday> diningToday(@PathVariable Long id) {
        return placeService.diningToday(id);
    }

    @GetMapping("/places/{id}")
    public PlaceDetail detail(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return placeService.detail(id, userId);
    }

    @PostMapping("/places/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceReviewResponse review(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                      @Valid @RequestBody PlaceReviewRequest request) {
        return placeService.review(userId, id, request);
    }
}
