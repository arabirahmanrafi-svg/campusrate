package com.campusrate.service;

import com.campusrate.dto.UniversityDtos.RankingEntry;
import com.campusrate.model.Category;
import com.campusrate.model.ReviewStats;
import com.campusrate.model.University;
import com.campusrate.repository.ReviewRepository;
import com.campusrate.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    @Test
    void schoolWithNoReviewsGetsTheGlobalMean() {
        assertThat(RankingService.bayesianScore(0, 0, 3.8, 10)).isEqualTo(3.8);
    }

    @Test
    void fewReviewsStayCloseToTheGlobalMean() {
        // 2 perfect reviews, global mean 3.5, C = 10 → (10*3.5 + 2*5) / 12 = 3.75
        assertThat(RankingService.bayesianScore(5.0, 2, 3.5, 10)).isCloseTo(3.75, within(0.0001));
    }

    @Test
    void manyReviewsMoveTowardTheSchoolsOwnAverage() {
        double score = RankingService.bayesianScore(4.6, 500, 3.5, 10);
        assertThat(score).isGreaterThan(4.55).isLessThan(4.6);
    }

    @Test
    void wellReviewedSchoolOutranksSchoolWithTwoPerfectReviews() {
        University tiny = school(1L, "Tiny College");
        tiny.applyStats(stats(2, 5.0));
        University big = school(2L, "Big State University");
        big.applyStats(stats(500, 4.6));

        List<RankingEntry> ranking = service(List.of(tiny, big), stats(502, 3.5)).rankings(Category.OVERALL, null, 10);

        assertThat(ranking).extracting(RankingEntry::name)
                .containsExactly("Big State University", "Tiny College");
        assertThat(ranking.get(0).rank()).isEqualTo(1);
    }

    @Test
    void limitCutsTheList() {
        University a = school(1L, "A");
        a.applyStats(stats(20, 4.0));
        University b = school(2L, "B");
        b.applyStats(stats(20, 3.0));

        List<RankingEntry> ranking = service(List.of(a, b), stats(40, 3.5)).rankings(Category.OVERALL, "", 1);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).name()).isEqualTo("A");
    }

    @Test
    void internationalScoresUseOnlyReviewsThatIncludeThem() {
        University school = school(1L, "Some University");
        school.applyStats(stats(10, 4.0));
        school.applyInternationalStats(4, 4.5); // only 4 of the 10 reviewers gave international ratings

        assertThat(school.reviewCountFor(Category.OVERALL)).isEqualTo(10);
        assertThat(school.reviewCountFor(Category.INTERNATIONAL)).isEqualTo(4);
        assertThat(school.averageFor(Category.INTERNATIONAL)).isEqualTo(4.5);
    }

    private static RankingService service(List<University> schools, ReviewStats global) {
        UniversityRepository universities = mock(UniversityRepository.class);
        ReviewRepository reviews = mock(ReviewRepository.class);
        when(universities.findByReviewCountGreaterThan(0)).thenReturn(schools);
        when(reviews.statsForAllReviews()).thenReturn(global);
        return new RankingService(universities, reviews, mock(JdbcClient.class), 10);
    }

    private static University school(Long id, String name) {
        University u = new University(name, "City", null, "United States", null);
        u.setId(id);
        return u;
    }

    /** Same average in every category, which is all these tests need. */
    private static ReviewStats stats(long count, double average) {
        return new ReviewStats(count, average, average, average, average, average, average);
    }
}
