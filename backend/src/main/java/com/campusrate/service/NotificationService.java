package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.NotificationResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * In-app notifications ("Someone answered your question", the weekly digest).
 * They're shown on the website; sending them as emails would only need one more
 * class that reads the same rows and talks to a mail server.
 */
@Service
public class NotificationService {

    private static final RowMapper<NotificationResponse> ROW = (rs, n) -> new NotificationResponse(
            rs.getLong("id"), rs.getString("message"), rs.getString("link"),
            rs.getBoolean("is_read"), rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbc;

    public NotificationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void notify(Long userId, String message, String link) {
        jdbc.sql("insert into notifications (user_id, message, link) values (:userId, :message, :link)")
                .param("userId", userId)
                .param("message", message.length() > 300 ? message.substring(0, 300) : message)
                .param("link", link)
                .update();
    }

    public List<NotificationResponse> recent(Long userId) {
        return jdbc.sql("select * from notifications where user_id = :userId order by created_at desc, id desc limit 50")
                .param("userId", userId).query(ROW).list();
    }

    public int unreadCount(Long userId) {
        return jdbc.sql("select count(*) from notifications where user_id = :userId and is_read = false")
                .param("userId", userId).query(Integer.class).single();
    }

    @Transactional
    public void markAllRead(Long userId) {
        jdbc.sql("update notifications set is_read = true where user_id = :userId and is_read = false")
                .param("userId", userId).update();
    }
}
