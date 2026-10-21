package com.campusrate.service;

import com.campusrate.dto.AdminDtos.AdminStats;
import com.campusrate.dto.AdminDtos.DayCount;
import com.campusrate.dto.AdminDtos.FlaggedReview;
import com.campusrate.dto.AdminDtos.PendingItems;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numbers and moderation queues for the admin dashboard. Every endpoint using this is admin-only. */
@Service
public class AdminService {

    static final int CHART_DAYS = 30;

    private final JdbcClient jdbc;
    private final PhotoService photoService;
    private final PlaceService placeService;
    private final InternationalService internationalService;

    public AdminService(JdbcClient jdbc, PhotoService photoService, PlaceService placeService,
                        InternationalService internationalService) {
        this.jdbc = jdbc;
        this.photoService = photoService;
        this.placeService = placeService;
        this.internationalService = internationalService;
    }

    public AdminStats stats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long activeUsers = jdbc.sql("""
                        select count(distinct user_id) from (
                            select user_id from reviews where created_at >= :since
                            union all select user_id from place_reviews where created_at >= :since
                            union all select user_id from questions where created_at >= :since
                            union all select user_id from answers where created_at >= :since
                        ) recent_activity
                        """)
                .param("since", weekAgo).query(Long.class).single();

        return new AdminStats(
                count("select count(*) from users"),
                count("select count(*) from reviews"),
                count("select count(*) from places where status = 'APPROVED'"),
                count("select count(*) from questions"),
                count("select count(*) from photos where status = 'APPROVED'"),
                activeUsers,
                count("select count(*) from reviews where status = 'FLAGGED'"),
                count("select count(*) from photos where status = 'PENDING'"),
                count("select count(*) from places where status = 'PENDING'"),
                count("select count(*) from community_links where status = 'PENDING'"),
                reviewsPerDay());
    }

    /** Reviews written on each of the last 30 days, including days with zero. */
    List<DayCount> reviewsPerDay() {
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(CHART_DAYS - 1);
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.sql("""
                        select date(created_at) as day, count(*) as review_count
                        from reviews where created_at >= :first
                        group by date(created_at)
                        """)
                .param("first", first.atStartOfDay())
                .query((rs, n) -> Map.entry(rs.getDate("day").toLocalDate(), rs.getLong("review_count")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));

        Map<LocalDate, Long> filled = new LinkedHashMap<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            filled.put(day, counts.getOrDefault(day, 0L));
        }
        List<DayCount> result = new ArrayList<>();
        filled.forEach((day, count) -> result.add(new DayCount(day.toString(), count)));
        return result;
    }

    /** Reviews hidden by the fake-review rules, with the reasons, oldest first. */
    public List<FlaggedReview> flaggedReviews() {
        record Row(Long reviewId, Long universityId, String universityName, String authorName, String authorEmail,
                   String body, double overall, LocalDateTime createdAt) {
        }
        List<Row> rows = jdbc.sql("""
                        select r.id, r.university_id, un.name as university_name, u.display_name, u.email,
                               r.body, r.overall, r.created_at
                        from reviews r
                        join users u on u.id = r.user_id
                        join universities un on un.id = r.university_id
                        where r.status = 'FLAGGED'
                        order by r.created_at
                        limit 100
                        """)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getLong("university_id"), rs.getString("university_name"),
                        rs.getString("display_name"), rs.getString("email"), rs.getString("body"),
                        rs.getDouble("overall"), rs.getObject("created_at", LocalDateTime.class)))
                .list();

        List<FlaggedReview> result = new ArrayList<>();
        for (Row row : rows) {
            List<String> reasons = jdbc.sql("select concat(rule, ': ', detail) from review_flags where review_id = :id")
                    .param("id", row.reviewId()).query(String.class).list();
            result.add(new FlaggedReview(row.reviewId(), row.universityId(), row.universityName(), row.authorName(),
                    row.authorEmail(), row.body(), row.overall(), row.createdAt(), reasons));
        }
        return result;
    }

    public PendingItems pending() {
        return new PendingItems(photoService.pending(), placeService.pending(), internationalService.pendingLinks());
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
