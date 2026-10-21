package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.FollowStatus;
import com.campusrate.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Following a school, plus the weekly digest: every Monday morning a background job tells
 * each follower what's new at the schools they follow.
 *
 * The digest runs on a schedule (@Scheduled) instead of during a web request, so a slow
 * job can never make the website slow for someone browsing.
 */
@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    record FollowRow(Long userId, Long universityId, String universityName) {
    }

    private final JdbcClient jdbc;
    private final AuthService authService;
    private final UniversityService universityService;
    private final NotificationService notificationService;

    public FollowService(JdbcClient jdbc, AuthService authService, UniversityService universityService,
                         NotificationService notificationService) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.universityService = universityService;
        this.notificationService = notificationService;
    }

    public FollowStatus status(Long universityId, Long userId) {
        int followers = jdbc.sql("select count(*) from follows where university_id = :id")
                .param("id", universityId).query(Integer.class).single();
        boolean following = userId != null && jdbc.sql(
                        "select count(*) from follows where university_id = :id and user_id = :userId")
                .param("id", universityId).param("userId", userId).query(Integer.class).single() > 0;
        return new FollowStatus(following, followers);
    }

    @Transactional
    public FollowStatus toggle(Long userId, Long universityId) {
        User user = authService.findUser(userId);
        universityService.findUniversity(universityId);
        int removed = jdbc.sql("delete from follows where user_id = :userId and university_id = :id")
                .param("userId", user.getId()).param("id", universityId).update();
        if (removed == 0) {
            jdbc.sql("insert into follows (user_id, university_id) values (:userId, :id)")
                    .param("userId", user.getId()).param("id", universityId).update();
        }
        return status(universityId, user.getId());
    }

    /** Every Monday at 9:00 (server time). */
    @Scheduled(cron = "0 0 9 * * MON")
    @Transactional
    public void scheduledWeeklyDigest() {
        sendWeeklyDigest();
    }

    /** Sends the digest now and returns how many notifications went out. Admins can run it from the dashboard. */
    @Transactional
    public int sendWeeklyDigest() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<FollowRow> follows = jdbc.sql("""
                        select f.user_id, f.university_id, u.name
                        from follows f join universities u on u.id = f.university_id
                        """)
                .query((rs, n) -> new FollowRow(rs.getLong("user_id"), rs.getLong("university_id"), rs.getString("name")))
                .list();

        int sent = 0;
        for (FollowRow follow : follows) {
            int reviews = jdbc.sql("""
                            select count(*) from reviews
                            where university_id = :id and status = 'PUBLISHED' and created_at >= :since
                            """)
                    .param("id", follow.universityId()).param("since", since).query(Integer.class).single();
            int questions = jdbc.sql("select count(*) from questions where university_id = :id and created_at >= :since")
                    .param("id", follow.universityId()).param("since", since).query(Integer.class).single();
            if (reviews + questions == 0) {
                continue; // nothing new: don't send an empty notification
            }
            notificationService.notify(follow.userId(),
                    "This week at " + follow.universityName() + ": " + plural(reviews, "new review")
                            + " and " + plural(questions, "new question") + ".",
                    "/universities/" + follow.universityId());
            sent++;
        }
        log.info("Weekly digest: sent {} notifications", sent);
        return sent;
    }

    static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
