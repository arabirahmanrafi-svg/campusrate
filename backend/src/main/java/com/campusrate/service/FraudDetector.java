package com.campusrate.service;

import com.campusrate.model.Review;
import com.campusrate.text.Similarity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rule-based fake review detection. Each new review is checked against three rules;
 * if any rule fires, the review is hidden (status FLAGGED) until an admin approves or removes it.
 *
 *  BURST           many reviews for the same school in a short time: a typical sign of
 *                  someone (or a bot) flooding a page.
 *  DUPLICATE_TEXT  the text is nearly the same as another recent review of that school,
 *                  measured with Jaccard similarity on 3-word "shingles".
 *  NEW_ACCOUNT     an account a few minutes old giving all 1s or all 5s.
 *
 * Rules are explainable: a moderator sees exactly why something was flagged. The trade-off
 * is that they only catch simple patterns; the rules are easy to tune with the constants below.
 */
@Service
public class FraudDetector {

    public record Flag(String rule, String detail) {
    }

    static final Duration BURST_WINDOW = Duration.ofHours(1);
    static final int BURST_LIMIT = 5;                 // flag the 6th review of one school within an hour
    static final double DUPLICATE_THRESHOLD = 0.6;    // 60% of 3-word phrases shared
    static final Duration NEW_ACCOUNT_AGE = Duration.ofMinutes(10);
    static final int RECENT_DAYS = 30;

    private final JdbcClient jdbc;

    public FraudDetector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Checks a review that was just saved. Returns the rules it broke (empty if none). */
    public List<Flag> check(Review review) {
        LocalDateTime now = LocalDateTime.now();
        Long universityId = review.getUniversity().getId();

        int recentCount = jdbc.sql("""
                        select count(*) from reviews
                        where university_id = :universityId and id <> :id and created_at >= :since
                        """)
                .param("universityId", universityId)
                .param("id", review.getId())
                .param("since", now.minus(BURST_WINDOW))
                .query(Integer.class).single();

        List<String> recentBodies = jdbc.sql("""
                        select body from reviews
                        where university_id = :universityId and id <> :id and created_at >= :since
                        order by created_at desc limit 200
                        """)
                .param("universityId", universityId)
                .param("id", review.getId())
                .param("since", now.minusDays(RECENT_DAYS))
                .query(String.class).list();

        int[] ratings = {review.getAcademics(), review.getCampusLife(), review.getHousing(),
                review.getFood(), review.getSafety()};

        return evaluate(review.getBody(), ratings, review.getUser().getCreatedAt(), now, recentCount, recentBodies);
    }

    /** The rules themselves, with no database access, so they can be unit tested directly. */
    static List<Flag> evaluate(String body, int[] ratings, LocalDateTime accountCreatedAt, LocalDateTime now,
                               int recentReviewsForSchool, List<String> recentBodies) {
        List<Flag> flags = new ArrayList<>();

        if (recentReviewsForSchool >= BURST_LIMIT) {
            flags.add(new Flag("BURST", recentReviewsForSchool + " other reviews of this school in the last hour"));
        }

        Set<String> mine = Similarity.shingles(body, 3);
        double highest = 0;
        for (String other : recentBodies) {
            highest = Math.max(highest, Similarity.jaccard(mine, Similarity.shingles(other, 3)));
        }
        if (highest >= DUPLICATE_THRESHOLD) {
            flags.add(new Flag("DUPLICATE_TEXT",
                    "Text is " + Math.round(highest * 100) + "% similar to another recent review"));
        }

        boolean allSame = true;
        for (int r : ratings) {
            allSame &= r == ratings[0];
        }
        boolean extreme = allSame && (ratings[0] == 1 || ratings[0] == 5);
        if (extreme && accountCreatedAt != null && accountCreatedAt.isAfter(now.minus(NEW_ACCOUNT_AGE))) {
            flags.add(new Flag("NEW_ACCOUNT", "Account created minutes ago and every rating is " + ratings[0]));
        }

        return flags;
    }

    public void saveFlags(Long reviewId, List<Flag> flags) {
        for (Flag flag : flags) {
            jdbc.sql("insert into review_flags (review_id, rule, detail) values (:reviewId, :rule, :detail)")
                    .param("reviewId", reviewId).param("rule", flag.rule()).param("detail", flag.detail())
                    .update();
        }
    }
}
