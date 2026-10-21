package com.campusrate.dto;

import com.campusrate.dto.CommunityDtos.LinkResponse;
import com.campusrate.dto.CommunityDtos.PhotoResponse;
import com.campusrate.dto.CommunityDtos.PlaceSummary;

import java.time.LocalDateTime;
import java.util.List;

/** Shapes for the admin dashboard and moderation queues. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record DayCount(String day, long count) {
    }

    public record AdminStats(
            long users, long reviews, long places, long questions, long photos,
            long activeUsersLast7Days,
            long openFlags, long pendingPhotos, long pendingPlaces, long pendingLinks,
            List<DayCount> reviewsPerDay
    ) {
    }

    public record FlaggedReview(Long reviewId, Long universityId, String universityName, String authorName,
                                String authorEmail, String body, double overall, LocalDateTime createdAt,
                                List<String> reasons) {
    }

    public record PendingItems(List<PhotoResponse> photos, List<PlaceSummary> places, List<LinkResponse> links) {
    }
}
