package com.campusrate.dto;

import com.campusrate.dto.CommunityDtos.PhotoResponse;
import com.campusrate.model.Review;
import com.campusrate.model.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * Sent by the browser to create or edit a review. Validation runs before the controller code.
     * The four international ratings are optional, but must be given all together.
     * {@code website} is the hidden honeypot field (see AuthDtos.RegisterRequest).
     */
    public record ReviewRequest(
            @NotNull Long universityId,
            @NotNull @Min(1) @Max(5) Integer academics,
            @NotNull @Min(1) @Max(5) Integer campusLife,
            @NotNull @Min(1) @Max(5) Integer housing,
            @NotNull @Min(1) @Max(5) Integer food,
            @NotNull @Min(1) @Max(5) Integer safety,
            @Min(1) @Max(5) Integer intlSupport,
            @Min(1) @Max(5) Integer visaHelp,
            @Min(1) @Max(5) Integer jobAvailability,
            @Min(1) @Max(5) Integer welcoming,
            @NotBlank @Size(min = 30, max = 2000, message = "must be between 30 and 2000 characters") String body,
            @Size(max = 100) String major,
            @Min(1950) @Max(2040) Integer gradYear,
            String website
    ) {
        /** Convenience constructor for reviews without international ratings (used in tests). */
        public ReviewRequest(Long universityId, Integer academics, Integer campusLife, Integer housing,
                             Integer food, Integer safety, String body, String major, Integer gradYear) {
            this(universityId, academics, campusLife, housing, food, safety,
                    null, null, null, null, body, major, gradYear, null);
        }

        public boolean hasAnyInternationalRating() {
            return intlSupport != null || visaHelp != null || jobAvailability != null || welcoming != null;
        }

        public boolean hasAllInternationalRatings() {
            return intlSupport != null && visaHelp != null && jobAvailability != null && welcoming != null;
        }
    }

    /** Who wrote a review, shown as badges next to their name. */
    public record Author(String name, String studentStatus, boolean international, String country, int reputation) {
        public static Author from(User u) {
            return new Author(u.getDisplayName(), u.getStudentStatus(), u.isInternational(),
                    u.publicCountry(), u.getReputation());
        }
    }

    public record ReviewResponse(
            Long id,
            Long universityId,
            String universityName,
            Author author,
            int academics, int campusLife, int housing, int food, int safety,
            Integer intlSupport, Integer visaHelp, Integer jobAvailability, Integer welcoming,
            double overall,
            String body,
            String major,
            Integer gradYear,
            int helpfulCount,
            String status,       // PUBLISHED, FLAGGED (waiting for a moderator) or REMOVED
            LocalDateTime createdAt,
            boolean mine,        // true if the logged-in user wrote it (shows Edit / Delete)
            boolean votedByMe,   // true if the logged-in user marked it helpful
            List<PhotoResponse> photos
    ) {
        /**
         * @param includeUniversityName only true when the university was loaded with the review,
         *                              so we never trigger an extra lazy query by accident.
         */
        public static ReviewResponse from(Review r, boolean includeUniversityName, boolean mine,
                                          boolean votedByMe, List<PhotoResponse> photos) {
            return new ReviewResponse(
                    r.getId(),
                    r.getUniversity().getId(),
                    includeUniversityName ? r.getUniversity().getName() : null,
                    Author.from(r.getUser()),
                    r.getAcademics(), r.getCampusLife(), r.getHousing(), r.getFood(), r.getSafety(),
                    r.getIntlSupport(), r.getVisaHelp(), r.getJobAvailability(), r.getWelcoming(),
                    UniversityDtos.round(r.getOverall()),
                    r.getBody(), r.getMajor(), r.getGradYear(),
                    r.getHelpfulCount(), r.getStatus(), r.getCreatedAt(), mine, votedByMe, photos);
        }
    }

    public record VoteResponse(boolean voted, int helpfulCount) {
    }

    /** A simple page wrapper so the JSON stays stable and easy for React to read. */
    public record PageResponse<T>(List<T> content, int page, int totalPages, long totalElements) {
    }
}
