package com.campusrate.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Shapes for the review insights and "ask the reviews" features. */
public final class InsightDtos {

    private InsightDtos() {
    }

    /** A sentence taken from a review, with the review it came from. */
    public record Quote(Long reviewId, String sentence) {
    }

    /** Average mood of reviews written in one semester. */
    public record SemesterPoint(String semester, double avgSentiment, double avgOverall, int count) {
    }

    public record Insights(
            int reviewCount,
            String summary,          // e.g. "Students rate Academics highest (4.5/5) and Food lowest (2.5/5)..."
            List<Quote> praise,      // the most typical positive sentences
            List<Quote> concerns,    // the most typical negative sentences
            int positivePercent,     // share of reviews with positive sentiment
            List<SemesterPoint> trend
    ) {
    }

    /** One review sentence that answers the question, with a link back to the review. */
    public record Citation(Long reviewId, String sentence, double score, String authorName, LocalDateTime createdAt) {
    }

    public record AskResponse(String question, String answer, List<Citation> citations) {
    }
}
