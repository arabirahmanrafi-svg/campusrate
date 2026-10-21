package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.DiningToday;
import com.campusrate.dto.CommunityDtos.ExtraFieldInfo;
import com.campusrate.dto.CommunityDtos.PlaceDetail;
import com.campusrate.dto.CommunityDtos.PlaceRequest;
import com.campusrate.dto.CommunityDtos.PlaceReviewRequest;
import com.campusrate.dto.CommunityDtos.PlaceReviewResponse;
import com.campusrate.dto.CommunityDtos.PlaceSummary;
import com.campusrate.dto.CommunityDtos.PlaceTypeInfo;
import com.campusrate.dto.CommunityDtos.RatingFieldInfo;
import com.campusrate.dto.UniversityDtos;
import com.campusrate.exception.ApiException;
import com.campusrate.model.PlaceType;
import com.campusrate.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dorms, dining halls, courses, campus jobs and clubs: one generic design for all five.
 *
 * Instead of five tables, five services and five controllers, there is one "places" table
 * with a type column, and the type-specific ratings are stored as JSON. PlaceType says which
 * ratings and extra fields each type has, and this class validates reviews against it.
 */
@Service
public class PlaceService {

    private static final TypeReference<Map<String, Integer>> RATINGS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> EXTRAS_TYPE = new TypeReference<>() {
    };

    private static final RowMapper<PlaceSummary> PLACE_ROW = (rs, n) -> new PlaceSummary(
            rs.getLong("id"), rs.getLong("university_id"), rs.getString("type"), rs.getString("name"),
            rs.getString("description"), rs.getString("status"), rs.getInt("review_count"),
            UniversityDtos.round(rs.getDouble("avg_rating")));

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final AuthService authService;
    private final PhotoService photoService;

    public PlaceService(JdbcClient jdbc, ObjectMapper json, AuthService authService, PhotoService photoService) {
        this.jdbc = jdbc;
        this.json = json;
        this.authService = authService;
        this.photoService = photoService;
    }

    /** The rating questions for every place type, so React can build its forms from them. */
    public List<PlaceTypeInfo> types() {
        return Arrays.stream(PlaceType.values())
                .map(t -> new PlaceTypeInfo(t.name(), t.getLabel(), t.getSingular(),
                        t.getRatings().stream().map(r -> new RatingFieldInfo(r.key(), r.label())).toList(),
                        t.getExtras().stream().map(e -> new ExtraFieldInfo(e.key(), e.label(), e.kind())).toList()))
                .toList();
    }

    public List<PlaceSummary> list(Long universityId, String type) {
        PlaceType placeType = parseType(type);
        return jdbc.sql("""
                        select * from places
                        where university_id = :universityId and type = :type and status = 'APPROVED'
                        order by review_count desc, name
                        """)
                .param("universityId", universityId).param("type", placeType.name())
                .query(PLACE_ROW).list();
    }

    /** A student suggests a missing place. It appears after an admin approves it (admins' own are approved at once). */
    @Transactional
    public PlaceSummary suggest(Long userId, Long universityId, PlaceRequest request) {
        User user = authService.findUser(userId);
        PlaceType type = parseType(request.type());
        String name = request.name().trim();

        int universityExists = jdbc.sql("select count(*) from universities where id = :id")
                .param("id", universityId).query(Integer.class).single();
        if (universityExists == 0) {
            throw ApiException.notFound("University not found.");
        }
        int duplicates = jdbc.sql("""
                        select count(*) from places
                        where university_id = :universityId and type = :type and lower(name) = lower(:name)
                          and status <> 'REJECTED'
                        """)
                .param("universityId", universityId).param("type", type.name()).param("name", name)
                .query(Integer.class).single();
        if (duplicates > 0) {
            throw ApiException.conflict("That " + type.getSingular() + " is already listed (or waiting for approval).");
        }

        String status = user.isAdmin() ? "APPROVED" : "PENDING";
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        jdbc.sql("""
                        insert into places (university_id, type, name, description, status, created_by)
                        values (:universityId, :type, :name, :description, :status, :createdBy)
                        """)
                .param("universityId", universityId).param("type", type.name()).param("name", name)
                .param("description", description).param("status", status).param("createdBy", user.getId())
                .update();

        return jdbc.sql("""
                        select * from places where university_id = :universityId and type = :type and name = :name
                        order by id desc limit 1
                        """)
                .param("universityId", universityId).param("type", type.name()).param("name", name)
                .query(PLACE_ROW).single();
    }

    public PlaceDetail detail(Long placeId, Long currentUserId) {
        PlaceSummary place = findApprovedPlace(placeId);
        PlaceType type = PlaceType.valueOf(place.type());
        String universityName = jdbc.sql("select name from universities where id = :id")
                .param("id", place.universityId()).query(String.class).single();

        List<PlaceReviewResponse> reviews = jdbc.sql("""
                        select pr.*, u.display_name from place_reviews pr join users u on u.id = pr.user_id
                        where pr.place_id = :placeId order by pr.created_at desc limit 100
                        """)
                .param("placeId", placeId)
                .query((rs, n) -> new PlaceReviewResponse(
                        rs.getLong("id"), rs.getLong("place_id"), rs.getString("display_name"),
                        UniversityDtos.round(rs.getDouble("rating")),
                        readJson(rs.getString("ratings"), RATINGS_TYPE),
                        readJson(rs.getString("extras"), EXTRAS_TYPE),
                        rs.getString("body"), rs.getObject("created_at", LocalDateTime.class),
                        Objects.equals(rs.getLong("user_id"), currentUserId)))
                .list();

        // Average of each type-specific rating, e.g. {"cleanliness": 3.8, "quietness": 2.5}.
        Map<String, Double> averages = new LinkedHashMap<>();
        for (PlaceType.RatingField field : type.getRatings()) {
            double avg = reviews.stream()
                    .map(r -> r.ratings().get(field.key()))
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .average().orElse(0);
            averages.put(field.key(), UniversityDtos.round(avg));
        }

        return new PlaceDetail(place, universityName, averages, reviews, photoService.approvedForPlace(placeId));
    }

    @Transactional
    public PlaceReviewResponse review(Long userId, Long placeId, PlaceReviewRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            throw ApiException.badRequest("Your review couldn't be saved. Please try again.");
        }
        User user = authService.findUser(userId);
        PlaceSummary place = findApprovedPlace(placeId);
        PlaceType type = PlaceType.valueOf(place.type());

        Map<String, Integer> ratings = validateRatings(type, request.ratings());
        Map<String, String> extras = validateExtras(type, request.extras());
        double overall = ratings.values().stream().mapToInt(Integer::intValue).average().orElse(0);

        int existing = jdbc.sql("select count(*) from place_reviews where place_id = :placeId and user_id = :userId")
                .param("placeId", placeId).param("userId", user.getId()).query(Integer.class).single();
        if (existing > 0) {
            throw ApiException.conflict("You've already reviewed this " + type.getSingular() + ".");
        }

        jdbc.sql("""
                        insert into place_reviews (place_id, user_id, rating, ratings, extras, body)
                        values (:placeId, :userId, :rating, :ratings, :extras, :body)
                        """)
                .param("placeId", placeId).param("userId", user.getId()).param("rating", overall)
                .param("ratings", writeJson(ratings)).param("extras", writeJson(extras))
                .param("body", request.body().trim())
                .update();

        // Keep the place's stored average in sync, in the same transaction.
        jdbc.sql("""
                        update places p
                        set review_count = (select count(*) from place_reviews where place_id = p.id),
                            avg_rating = (select coalesce(avg(rating), 0) from place_reviews where place_id = p.id)
                        where p.id = :placeId
                        """)
                .param("placeId", placeId).update();

        return new PlaceReviewResponse(null, placeId, user.getDisplayName(), UniversityDtos.round(overall),
                ratings, extras, request.body().trim(), LocalDateTime.now(), true);
    }

    /** Dining reviews from the last 24 hours, best first: "What's good today". */
    public List<DiningToday> diningToday(Long universityId) {
        return jdbc.sql("""
                        select p.id as place_id, p.name, pr.rating, pr.extras, pr.body, pr.created_at
                        from place_reviews pr join places p on p.id = pr.place_id
                        where p.university_id = :universityId and p.type = 'DINING' and p.status = 'APPROVED'
                          and pr.created_at >= :since
                        order by pr.rating desc, pr.created_at desc
                        limit 10
                        """)
                .param("universityId", universityId).param("since", LocalDateTime.now().minusHours(24))
                .query((rs, n) -> new DiningToday(rs.getLong("place_id"), rs.getString("name"),
                        readJson(rs.getString("extras"), EXTRAS_TYPE).get("meal"),
                        UniversityDtos.round(rs.getDouble("rating")), rs.getString("body"),
                        rs.getObject("created_at", LocalDateTime.class)))
                .list();
    }

    // ------------------------------------------------------------------ moderation

    public List<PlaceSummary> pending() {
        return jdbc.sql("select * from places where status = 'PENDING' order by created_at").query(PLACE_ROW).list();
    }

    @Transactional
    public void setStatus(Long placeId, String status) {
        int updated = jdbc.sql("update places set status = :status where id = :id")
                .param("status", status).param("id", placeId).update();
        if (updated == 0) {
            throw ApiException.notFound("Place not found.");
        }
    }

    // ------------------------------------------------------------------ validation helpers

    /** Every rating question of the type must be answered with 1 to 5; unknown keys are rejected. */
    static Map<String, Integer> validateRatings(PlaceType type, Map<String, Integer> given) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (PlaceType.RatingField field : type.getRatings()) {
            Integer value = given == null ? null : given.get(field.key());
            if (value == null || value < 1 || value > 5) {
                throw ApiException.badRequest("Please rate \"" + field.label() + "\" from 1 to 5 stars.");
            }
            result.put(field.key(), value);
        }
        if (given != null && given.keySet().stream().anyMatch(key -> !type.hasRating(key))) {
            throw ApiException.badRequest("Unknown rating for a " + type.getSingular() + ".");
        }
        return result;
    }

    /** Extra details are optional; blank ones are dropped, numbers and yes/no answers are checked. */
    static Map<String, String> validateExtras(PlaceType type, Map<String, String> given) {
        Map<String, String> result = new LinkedHashMap<>();
        if (given == null) {
            return result;
        }
        for (PlaceType.ExtraField field : type.getExtras()) {
            String value = given.get(field.key());
            if (value == null || value.isBlank()) {
                continue;
            }
            value = value.trim();
            if (value.length() > 100) {
                throw ApiException.badRequest("\"" + field.label() + "\" is too long (100 characters max).");
            }
            if (field.kind().equals("number") && !value.matches("\\d{1,5}(\\.\\d{1,2})?")) {
                throw ApiException.badRequest("\"" + field.label() + "\" must be a number.");
            }
            if (field.kind().equals("yesno") && !value.equals("yes") && !value.equals("no")) {
                throw ApiException.badRequest("\"" + field.label() + "\" must be yes or no.");
            }
            result.put(field.key(), value);
        }
        if (given.keySet().stream().anyMatch(key -> !type.hasExtra(key))) {
            throw ApiException.badRequest("Unknown detail for a " + type.getSingular() + ".");
        }
        return result;
    }

    private PlaceSummary findApprovedPlace(Long placeId) {
        return jdbc.sql("select * from places where id = :id and status = 'APPROVED'")
                .param("id", placeId).query(PLACE_ROW).optional()
                .orElseThrow(() -> ApiException.notFound("Place not found."));
    }

    private static PlaceType parseType(String type) {
        try {
            return PlaceType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("Unknown place type: " + type);
        }
    }

    private <T> T readJson(String text, TypeReference<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt JSON in place_reviews", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
