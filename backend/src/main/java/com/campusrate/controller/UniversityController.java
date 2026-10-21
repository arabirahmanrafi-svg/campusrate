package com.campusrate.controller;

import com.campusrate.dto.CommunityDtos.CostRequest;
import com.campusrate.dto.CommunityDtos.CostSummary;
import com.campusrate.dto.CommunityDtos.FollowStatus;
import com.campusrate.dto.CommunityDtos.InternationalSummary;
import com.campusrate.dto.CommunityDtos.LinkRequest;
import com.campusrate.dto.CommunityDtos.LinkResponse;
import com.campusrate.dto.InsightDtos.AskResponse;
import com.campusrate.dto.InsightDtos.Insights;
import com.campusrate.dto.ReviewDtos.PageResponse;
import com.campusrate.dto.ReviewDtos.ReviewResponse;
import com.campusrate.dto.UniversityDtos.FitRequest;
import com.campusrate.dto.UniversityDtos.FitResult;
import com.campusrate.dto.UniversityDtos.RankingEntry;
import com.campusrate.dto.UniversityDtos.TrendingEntry;
import com.campusrate.dto.UniversityDtos.UniversityDetail;
import com.campusrate.dto.UniversityDtos.UniversitySummary;
import com.campusrate.dto.UniversityDtos.ValuePoint;
import com.campusrate.model.Category;
import com.campusrate.service.ExploreService;
import com.campusrate.service.FollowService;
import com.campusrate.service.InsightService;
import com.campusrate.service.InternationalService;
import com.campusrate.service.RankingService;
import com.campusrate.service.ReviewService;
import com.campusrate.service.UniversityService;
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
import java.util.Map;

/** Universities: search, pages, reviews, insights, international info, rankings and discovery. */
@RestController
@RequestMapping("/api")
public class UniversityController {

    private final UniversityService universityService;
    private final ReviewService reviewService;
    private final RankingService rankingService;
    private final InsightService insightService;
    private final InternationalService internationalService;
    private final FollowService followService;
    private final ExploreService exploreService;

    public UniversityController(UniversityService universityService, ReviewService reviewService,
                                RankingService rankingService, InsightService insightService,
                                InternationalService internationalService, FollowService followService,
                                ExploreService exploreService) {
        this.universityService = universityService;
        this.reviewService = reviewService;
        this.rankingService = rankingService;
        this.insightService = insightService;
        this.internationalService = internationalService;
        this.followService = followService;
        this.exploreService = exploreService;
    }

    // ------------------------------------------------------------------ schools

    /** GET /api/universities?query=mich (typos allowed: "michgan" works too) */
    @GetMapping("/universities")
    public List<UniversitySummary> search(@RequestParam(defaultValue = "") String query) {
        return universityService.search(query);
    }

    @GetMapping("/universities/{id}")
    public UniversityDetail get(@PathVariable Long id) {
        return universityService.getById(id);
    }

    /** GET /api/universities/1/reviews?sort=helpful&page=0&international=true&country=Bangladesh */
    @GetMapping("/universities/{id}/reviews")
    public PageResponse<ReviewResponse> reviews(@PathVariable Long id,
                                                @RequestParam(defaultValue = "newest") String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "false") boolean international,
                                                @RequestParam(required = false) String country,
                                                @AuthenticationPrincipal Long userId) {
        return reviewService.listForUniversity(id, sort, page, international, country, userId);
    }

    @GetMapping("/universities/{id}/insights")
    public Insights insights(@PathVariable Long id) {
        return insightService.insights(id);
    }

    /** GET /api/universities/1/ask?q=is parking hard */
    @GetMapping("/universities/{id}/ask")
    public AskResponse ask(@PathVariable Long id, @RequestParam("q") String question) {
        return insightService.ask(id, question);
    }

    // ------------------------------------------------------------------ international students

    @GetMapping("/universities/{id}/international")
    public InternationalSummary international(@PathVariable Long id) {
        return internationalService.summary(id);
    }

    @PostMapping("/universities/{id}/costs")
    public CostSummary reportCosts(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                   @Valid @RequestBody CostRequest request) {
        return internationalService.reportCosts(userId, id, request);
    }

    @PostMapping("/universities/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse suggestLink(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                    @Valid @RequestBody LinkRequest request) {
        return internationalService.suggestLink(userId, id, request);
    }

    // ------------------------------------------------------------------ following

    @GetMapping("/universities/{id}/follow")
    public FollowStatus followStatus(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return followService.status(id, userId);
    }

    @PostMapping("/universities/{id}/follow")
    public FollowStatus toggleFollow(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return followService.toggle(userId, id);
    }

    // ------------------------------------------------------------------ rankings and discovery

    /** GET /api/rankings?category=international&country=Canada */
    @GetMapping("/rankings")
    public List<RankingEntry> rankings(@RequestParam(defaultValue = "overall") String category,
                                       @RequestParam(required = false) String country,
                                       @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return rankingService.rankings(Category.fromParam(category), country, safeLimit);
    }

    /** GET /api/compare?ids=1,2,3 */
    @GetMapping("/compare")
    public List<UniversityDetail> compare(@RequestParam List<Long> ids) {
        return universityService.compare(ids);
    }

    @GetMapping("/explore/trending")
    public List<TrendingEntry> trending() {
        return exploreService.trending();
    }

    @GetMapping("/explore/value")
    public List<ValuePoint> valueForMoney() {
        return exploreService.valueForMoney();
    }

    /** The "find my fit" quiz. Works without logging in. */
    @PostMapping("/fit")
    public List<FitResult> findFit(@RequestBody FitRequest request) {
        return exploreService.findFit(request);
    }

    @GetMapping("/countries")
    public List<String> countries() {
        return universityService.countries();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
