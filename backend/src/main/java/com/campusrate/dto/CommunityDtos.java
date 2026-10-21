package com.campusrate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request and response shapes for places, photos, Q&A, follows, notifications and international features. */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    // ---------------------------------------------------------------- photos

    public record PhotoResponse(Long id, Long universityId, String url, String caption, String uploaderName,
                                Long placeId, Long reviewId, String status, LocalDateTime createdAt) {
    }

    // ---------------------------------------------------------------- places

    public record PlaceTypeInfo(String type, String label, String singular,
                                List<RatingFieldInfo> ratings, List<ExtraFieldInfo> extras) {
    }

    public record RatingFieldInfo(String key, String label) {
    }

    public record ExtraFieldInfo(String key, String label, String kind) {
    }

    public record PlaceSummary(Long id, Long universityId, String type, String name, String description,
                               String status, int reviewCount, double avgRating) {
    }

    public record PlaceRequest(
            @NotBlank @Pattern(regexp = "DORM|DINING|COURSE|JOB|CLUB") String type,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description
    ) {
    }

    public record PlaceReviewRequest(
            @NotNull Map<String, Integer> ratings,
            Map<String, String> extras,
            @NotBlank @Size(min = 20, max = 1500, message = "must be between 20 and 1500 characters") String body,
            String website
    ) {
    }

    public record PlaceReviewResponse(Long id, Long placeId, String authorName, double rating,
                                      Map<String, Integer> ratings, Map<String, String> extras,
                                      String body, LocalDateTime createdAt, boolean mine) {
    }

    public record PlaceDetail(PlaceSummary place, String universityName, Map<String, Double> averages,
                              List<PlaceReviewResponse> reviews, List<PhotoResponse> photos) {
    }

    /** One entry in the "What's good today" dining feed. */
    public record DiningToday(Long placeId, String placeName, String meal, double rating,
                              String body, LocalDateTime createdAt) {
    }

    // ---------------------------------------------------------------- Q&A

    public record QuestionRequest(
            @NotBlank @Size(min = 10, max = 150, message = "must be between 10 and 150 characters") String title,
            @Size(max = 2000) String body
    ) {
    }

    public record AnswerRequest(
            @NotBlank @Size(min = 5, max = 2000, message = "must be between 5 and 2000 characters") String body
    ) {
    }

    public record QuestionSummary(Long id, Long universityId, String title, String body, String authorName,
                                  int answerCount, LocalDateTime createdAt) {
    }

    public record AnswerResponse(Long id, String body, String authorName, int authorReputation, String authorStatus,
                                 int votes, boolean votedByMe, boolean mine, LocalDateTime createdAt) {
    }

    public record QuestionDetail(QuestionSummary question, String universityName, boolean mine,
                                 List<AnswerResponse> answers) {
    }

    public record AnswerVoteResponse(boolean voted, int votes) {
    }

    // ---------------------------------------------------------------- follows and notifications

    public record FollowStatus(boolean following, int followers) {
    }

    public record NotificationResponse(Long id, String message, String link, boolean read, LocalDateTime createdAt) {
    }

    // ---------------------------------------------------------------- international students

    /** Monthly living costs in US dollars, plus optional yearly tuition. */
    public record CostRequest(
            @NotNull @Min(0) @Max(20000) Integer rent,
            @NotNull @Min(0) @Max(5000) Integer groceries,
            @NotNull @Min(0) @Max(5000) Integer transport,
            @NotNull @Min(0) @Max(1000) Integer phone,
            @Min(0) @Max(200000) Integer tuitionPerYear
    ) {
    }

    /**
     * Typical costs at a school: the MEDIAN of what students reported, not the average,
     * so one student paying luxury rent doesn't skew the numbers.
     */
    public record CostSummary(int reports, Integer rent, Integer groceries, Integer transport, Integer phone,
                              Integer monthlyTotal, Integer tuitionPerYear, Integer yearlyTotal) {
    }

    public record LinkRequest(
            @NotBlank @Pattern(regexp = "FOOD|ASSOCIATION|OTHER") String kind,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://") String url,
            @Size(max = 300) String description
    ) {
    }

    public record LinkResponse(Long id, Long universityId, String kind, String name, String url,
                               String description, String status) {
    }

    public record CountryCount(String country, long count) {
    }

    public record InternationalSummary(int intlReviewCount, double avgInternational,
                                       double avgIntlSupport, double avgVisaHelp,
                                       double avgJobAvailability, double avgWelcoming,
                                       List<CountryCount> countries, CostSummary cost,
                                       List<LinkResponse> links) {
    }
}
