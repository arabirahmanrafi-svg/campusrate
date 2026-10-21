package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.AnswerRequest;
import com.campusrate.dto.CommunityDtos.AnswerResponse;
import com.campusrate.dto.CommunityDtos.AnswerVoteResponse;
import com.campusrate.dto.CommunityDtos.QuestionDetail;
import com.campusrate.dto.CommunityDtos.QuestionRequest;
import com.campusrate.dto.CommunityDtos.QuestionSummary;
import com.campusrate.exception.ApiException;
import com.campusrate.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Q&A: prospective students ask, current students answer. Helpful answers earn the
 * answerer reputation points, which show next to their name.
 */
@Service
public class QuestionService {

    private static final RowMapper<QuestionSummary> QUESTION_ROW = (rs, n) -> new QuestionSummary(
            rs.getLong("id"), rs.getLong("university_id"), rs.getString("title"), rs.getString("body"),
            rs.getString("display_name"), rs.getInt("answer_count"), rs.getObject("created_at", LocalDateTime.class));

    private static final String QUESTION_SELECT = """
            select q.*, u.display_name from questions q join users u on u.id = q.user_id
            """;

    private final JdbcClient jdbc;
    private final AuthService authService;
    private final UniversityService universityService;
    private final NotificationService notificationService;

    public QuestionService(JdbcClient jdbc, AuthService authService, UniversityService universityService,
                           NotificationService notificationService) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.universityService = universityService;
        this.notificationService = notificationService;
    }

    public List<QuestionSummary> list(Long universityId) {
        return jdbc.sql(QUESTION_SELECT + " where q.university_id = :id order by q.created_at desc limit 100")
                .param("id", universityId).query(QUESTION_ROW).list();
    }

    @Transactional
    public QuestionSummary ask(Long userId, Long universityId, QuestionRequest request) {
        User user = authService.findUser(userId);
        universityService.findUniversity(universityId);
        String body = request.body() == null || request.body().isBlank() ? null : request.body().trim();

        jdbc.sql("insert into questions (university_id, user_id, title, body) values (:universityId, :userId, :title, :body)")
                .param("universityId", universityId).param("userId", user.getId())
                .param("title", request.title().trim()).param("body", body)
                .update();

        return jdbc.sql(QUESTION_SELECT + " where q.user_id = :userId order by q.id desc limit 1")
                .param("userId", user.getId()).query(QUESTION_ROW).single();
    }

    public QuestionDetail detail(Long questionId, Long currentUserId) {
        QuestionSummary question = findQuestion(questionId);
        String universityName = universityService.findUniversity(question.universityId()).getName();

        Long authorId = jdbc.sql("select user_id from questions where id = :id")
                .param("id", questionId).query(Long.class).single();

        Set<Long> voted = currentUserId == null ? Set.of() : jdbc.sql("""
                        select v.answer_id from answer_votes v join answers a on a.id = v.answer_id
                        where v.user_id = :userId and a.question_id = :questionId
                        """)
                .param("userId", currentUserId).param("questionId", questionId)
                .query(Long.class).list().stream().collect(Collectors.toSet());

        List<AnswerResponse> answers = jdbc.sql("""
                        select a.*, u.display_name, u.reputation, u.student_status
                        from answers a join users u on u.id = a.user_id
                        where a.question_id = :questionId
                        order by a.votes desc, a.created_at
                        """)
                .param("questionId", questionId)
                .query((rs, n) -> new AnswerResponse(
                        rs.getLong("id"), rs.getString("body"), rs.getString("display_name"),
                        rs.getInt("reputation"), rs.getString("student_status"), rs.getInt("votes"),
                        voted.contains(rs.getLong("id")),
                        Objects.equals(rs.getLong("user_id"), currentUserId),
                        rs.getObject("created_at", LocalDateTime.class)))
                .list();

        return new QuestionDetail(question, universityName, Objects.equals(authorId, currentUserId), answers);
    }

    @Transactional
    public AnswerResponse answer(Long userId, Long questionId, AnswerRequest request) {
        User user = authService.findUser(userId);
        QuestionSummary question = findQuestion(questionId);

        jdbc.sql("insert into answers (question_id, user_id, body) values (:questionId, :userId, :body)")
                .param("questionId", questionId).param("userId", user.getId()).param("body", request.body().trim())
                .update();
        jdbc.sql("update questions set answer_count = answer_count + 1 where id = :id").param("id", questionId).update();

        // Let the person who asked know, unless they answered their own question.
        Long askerId = jdbc.sql("select user_id from questions where id = :id")
                .param("id", questionId).query(Long.class).single();
        if (!askerId.equals(user.getId())) {
            notificationService.notify(askerId,
                    user.getDisplayName() + " answered your question \"" + question.title() + "\"",
                    "/questions/" + questionId);
        }

        Long answerId = jdbc.sql("select max(id) from answers where question_id = :questionId and user_id = :userId")
                .param("questionId", questionId).param("userId", user.getId()).query(Long.class).single();
        return new AnswerResponse(answerId, request.body().trim(), user.getDisplayName(), user.getReputation(),
                user.getStudentStatus(), 0, false, true, LocalDateTime.now());
    }

    /** Upvote an answer, or remove the upvote if already given. The answer's author gains or loses 1 reputation. */
    @Transactional
    public AnswerVoteResponse toggleVote(Long userId, Long answerId) {
        User user = authService.findUser(userId);
        Map<String, Object> answer = jdbc.sql("select user_id from answers where id = :id")
                .param("id", answerId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Answer not found."));
        long authorId = ((Number) answer.get("user_id")).longValue();
        if (authorId == user.getId()) {
            throw ApiException.badRequest("You can't upvote your own answer.");
        }

        int removed = jdbc.sql("delete from answer_votes where user_id = :userId and answer_id = :answerId")
                .param("userId", user.getId()).param("answerId", answerId).update();
        boolean voted = removed == 0;
        if (voted) {
            jdbc.sql("insert into answer_votes (user_id, answer_id) values (:userId, :answerId)")
                    .param("userId", user.getId()).param("answerId", answerId).update();
        }
        int delta = voted ? 1 : -1;
        jdbc.sql("update answers set votes = greatest(0, votes + :delta) where id = :id")
                .param("delta", delta).param("id", answerId).update();
        jdbc.sql("update users set reputation = greatest(0, reputation + :delta) where id = :id")
                .param("delta", delta).param("id", authorId).update();

        int votes = jdbc.sql("select votes from answers where id = :id").param("id", answerId).query(Integer.class).single();
        return new AnswerVoteResponse(voted, votes);
    }

    private QuestionSummary findQuestion(Long questionId) {
        return jdbc.sql(QUESTION_SELECT + " where q.id = :id")
                .param("id", questionId).query(QUESTION_ROW).optional()
                .orElseThrow(() -> ApiException.notFound("Question not found."));
    }
}
