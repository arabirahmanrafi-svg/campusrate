package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.CostSummary;
import com.campusrate.dto.UniversityDtos;
import com.campusrate.dto.UniversityDtos.FitRequest;
import com.campusrate.dto.UniversityDtos.FitResult;
import com.campusrate.dto.UniversityDtos.TrendingEntry;
import com.campusrate.dto.UniversityDtos.ValuePoint;
import com.campusrate.model.Category;
import com.campusrate.model.University;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Discovery features: trending schools, the "worth the money" chart and the "find my fit" quiz. */
@Service
public class ExploreService {

    private static final Logger log = LoggerFactory.getLogger(ExploreService.class);

    static final int TREND_DAYS = 30;
    static final int MIN_REVIEWS_PER_PERIOD = 3;

    private static final Map<Category, String> LABELS = new EnumMap<>(Category.class);

    static {
        LABELS.put(Category.ACADEMICS, "Academics");
        LABELS.put(Category.CAMPUS_LIFE, "Campus life");
        LABELS.put(Category.HOUSING, "Housing");
        LABELS.put(Category.FOOD, "Food");
        LABELS.put(Category.SAFETY, "Safety");
        LABELS.put(Category.INTERNATIONAL, "International support");
    }

    record PeriodAverage(Long universityId, double average, int count) {
    }

    private final JdbcClient jdbc;
    private final UniversityService universityService;
    private final RankingService rankingService;

    public ExploreService(JdbcClient jdbc, UniversityService universityService, RankingService rankingService) {
        this.jdbc = jdbc;
        this.universityService = universityService;
        this.rankingService = rankingService;
    }

    // ------------------------------------------------------------------ trending

    /**
     * Every hour: for each school, compare the average rating of the last 30 days with the
     * average before that. A school needs at least 3 reviews in both periods to get a trend,
     * so one new review can't make a school "trending".
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recomputeTrends() {
        LocalDateTime since = LocalDateTime.now().minusDays(TREND_DAYS);
        Map<Long, PeriodAverage> recent = periodAverages("created_at >= :since", since);
        Map<Long, PeriodAverage> before = periodAverages("created_at < :since", since);

        jdbc.sql("update universities set trend_delta = 0 where trend_delta <> 0").update();
        int updated = 0;
        for (PeriodAverage now : recent.values()) {
            PeriodAverage then = before.get(now.universityId());
            if (then != null && now.count() >= MIN_REVIEWS_PER_PERIOD && then.count() >= MIN_REVIEWS_PER_PERIOD) {
                jdbc.sql("update universities set trend_delta = :delta where id = :id")
                        .param("delta", now.average() - then.average()).param("id", now.universityId()).update();
                updated++;
            }
        }
        log.info("Trends recomputed for {} universities", updated);
    }

    private Map<Long, PeriodAverage> periodAverages(String condition, LocalDateTime since) {
        return jdbc.sql("select university_id, avg(overall) as average, count(*) as review_count from reviews "
                        + "where status = 'PUBLISHED' and " + condition + " group by university_id")
                .param("since", since)
                .query((rs, n) -> new PeriodAverage(rs.getLong("university_id"), rs.getDouble("average"),
                        rs.getInt("review_count")))
                .list().stream()
                .collect(Collectors.toMap(PeriodAverage::universityId, p -> p));
    }

    public List<TrendingEntry> trending() {
        return jdbc.sql("""
                        select id, name, city, country, avg_overall, review_count, trend_delta
                        from universities where trend_delta > 0
                        order by trend_delta desc limit 5
                        """)
                .query((rs, n) -> new TrendingEntry(rs.getLong("id"), rs.getString("name"), rs.getString("city"),
                        rs.getString("country"), UniversityDtos.round(rs.getDouble("avg_overall")),
                        rs.getInt("review_count"), UniversityDtos.round(rs.getDouble("trend_delta"))))
                .list();
    }

    // ------------------------------------------------------------------ worth the money

    /** Schools with both reviews and cost reports: yearly cost vs. average rating. */
    public List<ValuePoint> valueForMoney() {
        Map<Long, CostSummary> costs = costsBySchool();
        List<ValuePoint> points = new ArrayList<>();
        for (University u : universityService.reviewedUniversities()) {
            CostSummary cost = costs.get(u.getId());
            if (cost != null && cost.yearlyTotal() != null) {
                points.add(new ValuePoint(u.getId(), u.getName(), u.getCountry(), cost.yearlyTotal(),
                        UniversityDtos.round(u.getAvgOverall()), u.getReviewCount(), cost.reports()));
            }
        }
        return points;
    }

    /** Every school's cost summary, from one query instead of one per school. */
    Map<Long, CostSummary> costsBySchool() {
        record Row(Long universityId, InternationalService.CostRow cost) {
        }
        Map<Long, List<InternationalService.CostRow>> grouped = jdbc.sql(
                        "select university_id, rent, groceries, transport, phone, tuition_per_year from cost_reports")
                .query((rs, n) -> new Row(rs.getLong("university_id"), new InternationalService.CostRow(
                        rs.getInt("rent"), rs.getInt("groceries"), rs.getInt("transport"), rs.getInt("phone"),
                        rs.getObject("tuition_per_year", Integer.class))))
                .list().stream()
                .collect(Collectors.groupingBy(Row::universityId,
                        Collectors.mapping(Row::cost, Collectors.toList())));

        Map<Long, CostSummary> result = new HashMap<>();
        grouped.forEach((id, rows) -> result.put(id, InternationalService.summarize(rows)));
        return result;
    }

    // ------------------------------------------------------------------ find my fit

    /**
     * Ranks reviewed schools by how well they match what the student cares about.
     *
     * Each category's Bayesian score (so thinly reviewed schools aren't over-trusted) is
     * multiplied by the student's importance weight (0-5); the weighted average out of 5
     * becomes a match percentage. Schools over the budget are left out; schools with no
     * cost data are kept, but shown without a cost.
     */
    public List<FitResult> findFit(FitRequest request) {
        Map<Category, Integer> weights = new LinkedHashMap<>();
        weights.put(Category.ACADEMICS, clamp(request.academics()));
        weights.put(Category.CAMPUS_LIFE, clamp(request.campusLife()));
        weights.put(Category.HOUSING, clamp(request.housing()));
        weights.put(Category.FOOD, clamp(request.food()));
        weights.put(Category.SAFETY, clamp(request.safety()));
        weights.put(Category.INTERNATIONAL, clamp(request.international()));
        if (weights.values().stream().allMatch(w -> w == 0)) {
            weights.replaceAll((category, w) -> 1); // nothing chosen: treat everything as equally important
        }

        Map<Category, Double> globalMeans = new EnumMap<>(Category.class);
        weights.keySet().forEach(c -> globalMeans.put(c, rankingService.globalMean(c)));
        Map<Long, CostSummary> costs = costsBySchool();

        record Scored(University school, double score, List<String> strengths, Integer yearlyCost) {
        }
        List<Scored> scored = new ArrayList<>();
        for (University u : universityService.reviewedUniversities()) {
            if (request.country() != null && !request.country().isBlank()
                    && !u.getCountry().equalsIgnoreCase(request.country().trim())) {
                continue;
            }
            CostSummary cost = costs.get(u.getId());
            Integer yearly = cost == null ? null : cost.yearlyTotal();
            if (request.maxYearlyCost() != null && yearly != null && yearly > request.maxYearlyCost()) {
                continue;
            }

            double weighted = 0;
            int totalWeight = 0;
            Map<Category, Double> categoryScores = new EnumMap<>(Category.class);
            for (Map.Entry<Category, Integer> w : weights.entrySet()) {
                if (w.getValue() == 0) continue;
                double s = rankingService.scoreFor(u, w.getKey(), globalMeans.get(w.getKey()));
                categoryScores.put(w.getKey(), s);
                weighted += s * w.getValue();
                totalWeight += w.getValue();
            }
            double average = totalWeight == 0 ? 0 : weighted / totalWeight;

            List<String> strengths = categoryScores.entrySet().stream()
                    .filter(e -> e.getValue() >= 3.5)
                    .sorted(Map.Entry.<Category, Double>comparingByValue().reversed())
                    .limit(2)
                    .map(e -> LABELS.get(e.getKey()))
                    .toList();
            scored.add(new Scored(u, average, strengths, yearly));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(10)
                .map(s -> new FitResult(s.school().getId(), s.school().getName(), s.school().getCity(),
                        s.school().getCountry(), (int) Math.round(s.score() / 5.0 * 100), s.strengths(),
                        s.yearlyCost(), s.school().getReviewCount()))
                .toList();
    }

    private static int clamp(int weight) {
        return Math.max(0, Math.min(5, weight));
    }
}
