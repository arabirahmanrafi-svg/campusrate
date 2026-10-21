package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.CostSummary;
import com.campusrate.dto.InsightDtos.Quote;
import com.campusrate.dto.InsightDtos.SemesterPoint;
import com.campusrate.exception.ApiException;
import com.campusrate.model.PlaceType;
import com.campusrate.security.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the pure logic behind search, fraud rules, insights, costs, places and rate limiting. */
class SmartFeaturesTest {

    // ---------------------------------------------------------------- search

    private static final List<SearchService.Entry> SCHOOLS = List.of(
            SearchService.toEntry(1L, "Eastern Michigan University", "Ypsilanti", "Michigan", "United States", 12),
            SearchService.toEntry(2L, "University of Michigan", "Ann Arbor", "Michigan", "United States", 30),
            SearchService.toEntry(3L, "Massachusetts Institute of Technology", "Cambridge", "Massachusetts", "United States", 5),
            SearchService.toEntry(4L, "University of Toronto", "Toronto", "Ontario", "Canada", 0),
            SearchService.toEntry(5L, "Bangladesh University of Engineering and Technology", "Dhaka", null, "Bangladesh", 2));

    @Test
    void searchFindsNamesCitiesAndInitials() {
        assertThat(SearchService.rank(SCHOOLS, "eastern", 5)).first().isEqualTo(1L);
        assertThat(SearchService.rank(SCHOOLS, "ann arbor", 5)).first().isEqualTo(2L);
        assertThat(SearchService.rank(SCHOOLS, "mit", 5)).first().isEqualTo(3L);
        assertThat(SearchService.rank(SCHOOLS, "buet", 5)).first().isEqualTo(5L);
    }

    @Test
    void searchToleratesTypos() {
        assertThat(SearchService.rank(SCHOOLS, "michgan", 5)).contains(1L, 2L);
        assertThat(SearchService.rank(SCHOOLS, "torronto", 5)).first().isEqualTo(4L);
        assertThat(SearchService.rank(SCHOOLS, "zzzzzz", 5)).isEmpty();
    }

    @Test
    void schoolsWithMoreReviewsBreakTies() {
        // Both contain "michigan" in their name; Michigan (30 reviews) comes before Eastern Michigan (12)
        // only when the match quality is equal. "University of Michigan" starts with neither word, so
        // check a query both match equally well.
        List<Long> ids = SearchService.rank(SCHOOLS, "michigan", 5);
        assertThat(ids).containsExactly(2L, 1L);
    }

    // ---------------------------------------------------------------- fraud rules

    private static final int[] MIXED = {4, 3, 5, 4, 2};
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);

    @Test
    void normalReviewIsNotFlagged() {
        var flags = FraudDetector.evaluate("Good professors and a friendly campus overall.", MIXED,
                NOW.minusMonths(3), NOW, 1, List.of("The food is bland but the library is nice."));
        assertThat(flags).isEmpty();
    }

    @Test
    void burstOfReviewsIsFlagged() {
        var flags = FraudDetector.evaluate("Some ordinary review text here.", MIXED, NOW.minusMonths(3), NOW, 5, List.of());
        assertThat(flags).extracting(FraudDetector.Flag::rule).containsExactly("BURST");
    }

    @Test
    void copiedTextIsFlagged() {
        String original = "Best university ever, amazing professors, amazing dorms, amazing food, go here now";
        var flags = FraudDetector.evaluate(original + "!", MIXED, NOW.minusMonths(3), NOW, 0, List.of(original));
        assertThat(flags).extracting(FraudDetector.Flag::rule).containsExactly("DUPLICATE_TEXT");
    }

    @Test
    void brandNewAccountGivingAllFivesIsFlagged() {
        var flags = FraudDetector.evaluate("Perfect in every way, nothing to complain about.", new int[]{5, 5, 5, 5, 5},
                NOW.minusMinutes(3), NOW, 0, List.of());
        assertThat(flags).extracting(FraudDetector.Flag::rule).containsExactly("NEW_ACCOUNT");
    }

    // ---------------------------------------------------------------- insights

    @Test
    void summaryNamesBestAndWorstCategories() {
        Map<String, Double> averages = new LinkedHashMap<>();
        averages.put("academics", 4.5);
        averages.put("campusLife", 4.0);
        averages.put("housing", 3.2);
        averages.put("food", 2.5);
        averages.put("safety", 4.1);

        String summary = InsightService.summarize(averages, 75, 12);

        assertThat(summary).contains("Academics highest (4.5/5)", "Food lowest (2.5/5)", "mostly positive", "75%");
    }

    @Test
    void reviewsAreGroupedBySemester() {
        List<InsightService.ReviewText> reviews = List.of(
                review(1L, 0.5, 4.0, LocalDateTime.of(2026, 2, 1, 10, 0)),   // Spring 2026
                review(2L, -0.5, 2.0, LocalDateTime.of(2026, 3, 1, 10, 0)),  // Spring 2026
                review(3L, 0.8, 5.0, LocalDateTime.of(2026, 9, 1, 10, 0)));  // Fall 2026

        List<SemesterPoint> trend = InsightService.semesterTrend(reviews);

        assertThat(trend).extracting(SemesterPoint::semester).containsExactly("Spring 2026", "Fall 2026");
        assertThat(trend.get(0).avgSentiment()).isEqualTo(0.0);
        assertThat(trend.get(0).count()).isEqualTo(2);
        assertThat(trend.get(1).avgOverall()).isEqualTo(5.0);
    }

    @Test
    void typicalQuotesPreferPointsManyStudentsMake() {
        List<InsightService.Candidate> candidates = List.of(
                new InsightService.Candidate(1L, "The professors are helpful and care about students.", 0.6),
                new InsightService.Candidate(2L, "Professors here are really helpful during office hours.", 0.6),
                new InsightService.Candidate(3L, "Helpful professors made my first year easier.", 0.6),
                new InsightService.Candidate(4L, "I liked the purple chairs in one lounge.", 0.6));

        List<Quote> quotes = InsightService.mostTypical(candidates, 2);

        assertThat(quotes).hasSize(2);
        assertThat(quotes).extracting(Quote::reviewId).doesNotContain(4L);
    }

    // ---------------------------------------------------------------- living costs

    @Test
    void costSummaryUsesMedians() {
        List<InternationalService.CostRow> rows = List.of(
                new InternationalService.CostRow(600, 200, 50, 30, 15000),
                new InternationalService.CostRow(700, 250, 60, 30, null),
                new InternationalService.CostRow(3000, 900, 400, 100, 16000)); // one luxury outlier

        CostSummary summary = InternationalService.summarize(rows);

        assertThat(summary.reports()).isEqualTo(3);
        assertThat(summary.rent()).isEqualTo(700);            // median, not the 1433 average
        assertThat(summary.monthlyTotal()).isEqualTo(1040);   // median of 880, 1040, 4400
        assertThat(summary.tuitionPerYear()).isEqualTo(15500); // median of the two who reported it
        assertThat(summary.yearlyTotal()).isEqualTo(1040 * 12 + 15500);
    }

    // ---------------------------------------------------------------- place reviews

    @Test
    void placeRatingsMustCoverEveryQuestion() {
        Map<String, Integer> missingOne = Map.of("taste", 4, "variety", 3, "portions", 5);
        assertThatThrownBy(() -> PlaceService.validateRatings(PlaceType.DINING, missingOne))
                .isInstanceOf(ApiException.class).hasMessageContaining("Value for money");

        Map<String, Integer> complete = Map.of("taste", 4, "variety", 3, "portions", 5, "value", 4);
        assertThat(PlaceService.validateRatings(PlaceType.DINING, complete)).hasSize(4);
    }

    @Test
    void placeExtrasAreChecked() {
        assertThat(PlaceService.validateExtras(PlaceType.JOB, Map.of("hourlyPay", "13.50", "hoursPerWeek", " ")))
                .containsExactly(Map.entry("hourlyPay", "13.50"));
        assertThatThrownBy(() -> PlaceService.validateExtras(PlaceType.JOB, Map.of("hourlyPay", "lots")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PlaceService.validateExtras(PlaceType.COURSE, Map.of("attendanceRequired", "maybe")))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------- photos

    @Test
    void imagesAreRecognisedByTheirFirstBytes() {
        assertThat(PhotoService.detectImageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("jpg");
        assertThat(PhotoService.detectImageType(new byte[]{(byte) 0x89, 'P', 'N', 'G'})).isEqualTo("png");
        assertThat(PhotoService.detectImageType("<script>alert(1)</script>".getBytes())).isNull();
    }

    // ---------------------------------------------------------------- rate limiting

    @Test
    void rateLimiterAllowsUpToTheLimitThenBlocksUntilTheWindowPasses() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(3, 60_000, clock);

        assertThat(limiter.tryAcquire("user:1")).isTrue();
        assertThat(limiter.tryAcquire("user:1")).isTrue();
        assertThat(limiter.tryAcquire("user:1")).isTrue();
        assertThat(limiter.tryAcquire("user:1")).isFalse();
        assertThat(limiter.tryAcquire("user:2")).isTrue(); // limits are per user

        clock.advance(60_001);
        assertThat(limiter.tryAcquire("user:1")).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private static InsightService.ReviewText review(Long id, double sentiment, double overall, LocalDateTime at) {
        return new InsightService.ReviewText(id, "text", sentiment, overall, at, "Student");
    }

    /** A clock the test can move forward, so the rate-limit test doesn't have to wait a minute. */
    static class MutableClock extends Clock {
        private long millis = 1_000_000;

        void advance(long ms) {
            millis += ms;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
