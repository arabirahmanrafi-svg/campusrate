package com.campusrate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: starts the whole app (controllers, security, services, Flyway, JPA)
 * against the MySQL test database (campusrate_test) and calls the real HTTP endpoints.
 * MySQL must be running, the same as when you run the app.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    /** Wipe the test database and rebuild it from the migrations, so every test run starts the same. */
    @TestConfiguration
    static class CleanDatabase {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    // University id 1 is Eastern Michigan University, from V2__seed_universities.sql
    private static final long EMU_ID = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void searchFindsSeededUniversity() throws Exception {
        mvc.perform(get("/api/universities").param("query", "eastern mich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Eastern Michigan University"));
    }

    @Test
    void writingRequiresLogin() throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(reviewJson(EMU_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidReviewReturnsFieldErrors() throws Exception {
        String token = registerAndGetToken();
        String tooShort = json.writeValueAsString(Map.of(
                "universityId", EMU_ID, "academics", 9, "campusLife", 4, "housing", 4,
                "food", 4, "safety", 4, "body", "too short"));

        mvc.perform(post("/api/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(tooShort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.academics").exists())
                .andExpect(jsonPath("$.fields.body").exists());
    }

    @Test
    void fullReviewFlow() throws Exception {
        String token = registerAndGetToken();
        long schoolId = 2L; // University of Michigan, untouched by the other tests

        // 1. Post a review
        mvc.perform(post("/api/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(schoolId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overall").value(4.0));

        // 2. The school's stored averages were updated in the same transaction
        mvc.perform(get("/api/universities/" + schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.avgAcademics").value(5.0));

        // 3. The review shows up, marked as mine
        mvc.perform(get("/api/universities/" + schoolId + "/reviews").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].mine").value(true));

        // 4. A second review of the same school is rejected
        mvc.perform(post("/api/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(schoolId)))
                .andExpect(status().isConflict());

        // 5. The school appears in rankings
        mvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + schoolId + ")]").exists());
    }

    @Test
    void searchToleratesTypos() throws Exception {
        mvc.perform(get("/api/universities").param("query", "michgan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'University of Michigan')]").exists());
    }

    @Test
    void diningReviewShowsUpInWhatsGoodToday() throws Exception {
        String token = registerAndGetToken();

        // "The Commons" at EMU comes from V4__coordinates_and_starter_places.sql
        String places = mvc.perform(get("/api/universities/" + EMU_ID + "/places").param("type", "DINING"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commonsId = json.readTree(places).get(0).get("id").asLong();

        String review = json.writeValueAsString(Map.of(
                "ratings", Map.of("taste", 5, "variety", 4, "portions", 4, "value", 3),
                "extras", Map.of("meal", "Chicken biryani"),
                "body", "The biryani today was honestly really good and fresh."));
        mvc.perform(post("/api/places/" + commonsId + "/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4.0));

        mvc.perform(get("/api/universities/" + EMU_ID + "/dining/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meal").value("Chicken biryani"));

        mvc.perform(get("/api/places/" + commonsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averages.taste").value(5.0))
                .andExpect(jsonPath("$.place.reviewCount").value(1));
    }

    @Test
    void answeringAQuestionNotifiesTheAsker() throws Exception {
        String asker = registerAndGetToken();
        String answerer = registerAndGetToken();

        String question = mvc.perform(post("/api/universities/3/questions").header("Authorization", "Bearer " + asker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Is it easy to find a campus job?"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long questionId = json.readTree(question).get("id").asLong();

        String answer = mvc.perform(post("/api/questions/" + questionId + "/answers").header("Authorization", "Bearer " + answerer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "Yes, the library and dining halls hire every semester."))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long answerId = json.readTree(answer).get("id").asLong();

        mvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + asker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        // You can't upvote your own answer, but the asker can.
        mvc.perform(post("/api/answers/" + answerId + "/vote").header("Authorization", "Bearer " + answerer))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/answers/" + answerId + "/vote").header("Authorization", "Bearer " + asker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votes").value(1));
    }

    @Test
    void suspiciousReviewIsHiddenUntilAnAdminApprovesIt() throws Exception {
        long schoolId = 4L;
        String newAccount = registerAndGetToken();
        String allFives = json.writeValueAsString(Map.of(
                "universityId", schoolId,
                "academics", 5, "campusLife", 5, "housing", 5, "food", 5, "safety", 5,
                "body", "Absolutely perfect school, everything is the best, no complaints at all."));

        // A minutes-old account giving all 5s trips the NEW_ACCOUNT rule.
        String created = mvc.perform(post("/api/reviews").header("Authorization", "Bearer " + newAccount)
                        .contentType(MediaType.APPLICATION_JSON).content(allFives))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FLAGGED"))
                .andReturn().getResponse().getContentAsString();
        long reviewId = json.readTree(created).get("id").asLong();

        mvc.perform(get("/api/universities/" + schoolId))
                .andExpect(jsonPath("$.reviewCount").value(0)); // hidden reviews don't count

        // Students can't see the moderation queue; the configured admin can.
        mvc.perform(get("/api/admin/flags").header("Authorization", "Bearer " + newAccount))
                .andExpect(status().isForbidden());
        String admin = registerAndGetToken("admin@test.edu");
        mvc.perform(get("/api/admin/flags").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reviewId == " + reviewId + ")]").exists());

        mvc.perform(post("/api/admin/reviews/" + reviewId + "/approve").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/universities/" + schoolId))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void insightsAndAskWork() throws Exception {
        mvc.perform(get("/api/universities/" + EMU_ID + "/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists());
        mvc.perform(get("/api/universities/" + EMU_ID + "/ask").param("q", "is parking hard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists());
    }

    // ------------------------------------------------------------------ helpers

    private String registerAndGetToken() throws Exception {
        return registerAndGetToken("student-" + UUID.randomUUID() + "@emich.edu");
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", email, "password", "password123", "displayName", "Test Student"));

        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = json.readTree(response);
        assertThat(node.get("token").asText()).isNotBlank();
        return node.get("token").asText();
    }

    private String reviewJson(long universityId) throws Exception {
        return json.writeValueAsString(Map.of(
                "universityId", universityId,
                "academics", 5, "campusLife", 4, "housing", 3, "food", 3, "safety", 5,
                "body", "Professors really care and class sizes are small. Dining options could be better.",
                "major", "Computer Science",
                "gradYear", 2027));
    }
}
