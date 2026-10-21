package com.campusrate.service;

import com.campusrate.dto.InsightDtos.AskResponse;
import com.campusrate.dto.InsightDtos.Citation;
import com.campusrate.dto.InsightDtos.Insights;
import com.campusrate.dto.InsightDtos.Quote;
import com.campusrate.dto.InsightDtos.SemesterPoint;
import com.campusrate.dto.UniversityDtos;
import com.campusrate.exception.ApiException;
import com.campusrate.model.University;
import com.campusrate.text.SentimentAnalyzer;
import com.campusrate.text.TextUtils;
import com.campusrate.text.TfIdfIndex;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Smart" features built from the review text, in plain Java with no outside AI service:
 *
 *  1. Insights: an at-a-glance summary of a school (best and worst categories, overall mood)
 *     plus the most TYPICAL praise and complaints, quoted from real reviews.
 *  2. Sentiment trend: how positive reviews were in each semester.
 *  3. Ask the reviews: type a question, get the review sentences that answer it, with
 *     links to the reviews they came from (the retrieval half of RAG, using TF-IDF).
 *
 * Results are quotes from real students, never generated text, so nothing is made up.
 */
@Service
public class InsightService {

    static final int MIN_REVIEWS_FOR_INSIGHTS = 3;

    /** The review fields these features need. */
    record ReviewText(Long id, String body, double sentiment, double overall, LocalDateTime createdAt, String author) {
    }

    /** A sentence that might be quoted. */
    record Candidate(Long reviewId, String sentence, double sentiment) {
    }

    private static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>();

    static {
        CATEGORY_LABELS.put("academics", "Academics");
        CATEGORY_LABELS.put("campusLife", "Campus life");
        CATEGORY_LABELS.put("housing", "Housing");
        CATEGORY_LABELS.put("food", "Food");
        CATEGORY_LABELS.put("safety", "Safety");
    }

    private final JdbcClient jdbc;
    private final UniversityService universityService;
    private final SentimentAnalyzer sentimentAnalyzer;

    public InsightService(JdbcClient jdbc, UniversityService universityService, SentimentAnalyzer sentimentAnalyzer) {
        this.jdbc = jdbc;
        this.universityService = universityService;
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    // ------------------------------------------------------------------ insights

    /** Cached per school; ReviewService clears the cache whenever a review changes. */
    @Cacheable(cacheNames = "insights", key = "#universityId")
    public Insights insights(Long universityId) {
        University university = universityService.findUniversity(universityId);
        List<ReviewText> reviews = publishedReviews(universityId);
        List<SemesterPoint> trend = semesterTrend(reviews);

        int positive = (int) reviews.stream().filter(r -> r.sentiment() > 0.1).count();
        int positivePercent = reviews.isEmpty() ? 0 : Math.round(100f * positive / reviews.size());

        if (reviews.size() < MIN_REVIEWS_FOR_INSIGHTS) {
            return new Insights(reviews.size(),
                    "Insights appear once a school has at least " + MIN_REVIEWS_FOR_INSIGHTS + " reviews.",
                    List.of(), List.of(), positivePercent, trend);
        }

        Map<String, Double> averages = new LinkedHashMap<>();
        averages.put("academics", university.getAvgAcademics());
        averages.put("campusLife", university.getAvgCampusLife());
        averages.put("housing", university.getAvgHousing());
        averages.put("food", university.getAvgFood());
        averages.put("safety", university.getAvgSafety());

        String summary = summarize(averages, positivePercent, reviews.size());

        List<Candidate> candidates = new ArrayList<>();
        for (ReviewText review : reviews) {
            for (String sentence : TextUtils.sentences(review.body())) {
                candidates.add(new Candidate(review.id(), sentence, sentimentAnalyzer.score(sentence)));
            }
        }
        List<Quote> praise = mostTypical(candidates.stream().filter(c -> c.sentiment() >= 0.25).toList(), 3);
        List<Quote> concerns = mostTypical(candidates.stream().filter(c -> c.sentiment() <= -0.2).toList(), 3);

        return new Insights(reviews.size(), summary, praise, concerns, positivePercent, trend);
    }

    /** e.g. "Students rate Academics highest (4.5/5) and Food lowest (2.5/5). 80% of reviews are positive." */
    static String summarize(Map<String, Double> averages, int positivePercent, int reviewCount) {
        String best = null;
        String worst = null;
        for (String key : averages.keySet()) {
            if (best == null || averages.get(key) > averages.get(best)) best = key;
            if (worst == null || averages.get(key) < averages.get(worst)) worst = key;
        }
        String mood = positivePercent >= 70 ? "mostly positive"
                : positivePercent >= 40 ? "mixed"
                : "mostly critical";
        StringBuilder text = new StringBuilder();
        text.append("Based on ").append(reviewCount).append(" reviews, students rate ")
                .append(CATEGORY_LABELS.get(best)).append(" highest (")
                .append(String.format(Locale.ROOT, "%.1f", averages.get(best))).append("/5)");
        if (!best.equals(worst)) {
            text.append(" and ").append(CATEGORY_LABELS.get(worst)).append(" lowest (")
                    .append(String.format(Locale.ROOT, "%.1f", averages.get(worst))).append("/5)");
        }
        text.append(". The tone of reviews is ").append(mood)
                .append(" (").append(positivePercent).append("% positive).");
        return text.toString();
    }

    /**
     * Picks the sentences most similar to the group as a whole, i.e. the points many students
     * make, not one-off remarks. Each sentence is scored by how much its words overlap with
     * the words of all the other sentences (cosine similarity to the group's "centroid"),
     * weighted by how strongly positive or negative it is. At most one quote per review.
     */
    static List<Quote> mostTypical(List<Candidate> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, Double> centroid = new HashMap<>();
        List<Map<String, Double>> vectors = new ArrayList<>();
        for (Candidate c : candidates) {
            Map<String, Double> vector = new HashMap<>();
            for (String term : new HashSet<>(TextUtils.terms(c.sentence()))) {
                vector.put(term, 1.0);
                centroid.merge(term, 1.0, Double::sum);
            }
            vectors.add(vector);
        }

        record Scored(Candidate candidate, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Map<String, Double> vector = vectors.get(i);
            // Remove the sentence's own words from the centroid so it can't "agree with itself".
            Map<String, Double> others = new HashMap<>(centroid);
            vector.keySet().forEach(term -> others.merge(term, -1.0, Double::sum));
            double typicality = cosine(vector, others);
            scored.add(new Scored(candidates.get(i), typicality * (0.5 + Math.abs(candidates.get(i).sentiment()))));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        List<Quote> result = new ArrayList<>();
        Set<Long> usedReviews = new HashSet<>();
        for (Scored s : scored) {
            if (result.size() == limit) break;
            if (usedReviews.add(s.candidate().reviewId())) {
                result.add(new Quote(s.candidate().reviewId(), s.candidate().sentence()));
            }
        }
        return result;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            dot += e.getValue() * b.getOrDefault(e.getKey(), 0.0);
        }
        double na = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double nb = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return na == 0 || nb == 0 ? 0 : dot / (na * nb);
    }

    // ------------------------------------------------------------------ sentiment trend

    /** Average sentiment and rating per semester, oldest first. */
    static List<SemesterPoint> semesterTrend(List<ReviewText> reviews) {
        record Bucket(int order, String label, List<ReviewText> items) {
        }
        Map<Integer, Bucket> buckets = new HashMap<>();
        for (ReviewText r : reviews) {
            int year = r.createdAt().getYear();
            int month = r.createdAt().getMonthValue();
            int term = month <= 5 ? 0 : month <= 7 ? 1 : 2; // Spring, Summer, Fall
            String label = (term == 0 ? "Spring " : term == 1 ? "Summer " : "Fall ") + year;
            int order = year * 3 + term;
            buckets.computeIfAbsent(order, o -> new Bucket(o, label, new ArrayList<>())).items().add(r);
        }
        return buckets.values().stream()
                .sorted(Comparator.comparingInt(Bucket::order))
                .map(b -> new SemesterPoint(b.label(),
                        UniversityDtos.round(b.items().stream().mapToDouble(ReviewText::sentiment).average().orElse(0)),
                        UniversityDtos.round(b.items().stream().mapToDouble(ReviewText::overall).average().orElse(0)),
                        b.items().size()))
                .toList();
    }

    // ------------------------------------------------------------------ ask the reviews

    public AskResponse ask(Long universityId, String question) {
        universityService.findUniversity(universityId);
        if (question == null || question.trim().length() < 3 || question.length() > 200) {
            throw ApiException.badRequest("Please ask a question between 3 and 200 characters.");
        }

        TfIdfIndex<Citation> index = new TfIdfIndex<>();
        for (ReviewText review : publishedReviews(universityId)) {
            for (String sentence : TextUtils.sentences(review.body())) {
                index.add(new Citation(review.id(), sentence, 0, review.author(), review.createdAt()), sentence);
            }
        }

        // Take the best matches, at most two sentences from any one review.
        List<Citation> citations = new ArrayList<>();
        Map<Long, Integer> perReview = new HashMap<>();
        for (TfIdfIndex.Match<Citation> match : index.search(question, 15, 0.08)) {
            Citation c = match.item();
            if (perReview.merge(c.reviewId(), 1, Integer::sum) <= 2 && citations.size() < 5) {
                citations.add(new Citation(c.reviewId(), c.sentence(), UniversityDtos.round(match.score()),
                        c.authorName(), c.createdAt()));
            }
        }

        String answer;
        if (citations.isEmpty()) {
            answer = "No reviews mention that yet. Try other words, or ask current students in the Q&A tab.";
        } else {
            long students = citations.stream().map(Citation::reviewId).distinct().count();
            answer = "Here's what " + students + (students == 1 ? " student" : " students")
                    + " said that matches your question:";
        }
        return new AskResponse(question.trim(), answer, citations);
    }

    private List<ReviewText> publishedReviews(Long universityId) {
        return jdbc.sql("""
                        select r.id, r.body, r.sentiment, r.overall, r.created_at, u.display_name
                        from reviews r join users u on u.id = r.user_id
                        where r.university_id = :id and r.status = 'PUBLISHED'
                        order by r.created_at
                        """)
                .param("id", universityId)
                .query((rs, n) -> new ReviewText(rs.getLong("id"), rs.getString("body"), rs.getDouble("sentiment"),
                        rs.getDouble("overall"), rs.getObject("created_at", LocalDateTime.class),
                        rs.getString("display_name")))
                .list();
    }
}
