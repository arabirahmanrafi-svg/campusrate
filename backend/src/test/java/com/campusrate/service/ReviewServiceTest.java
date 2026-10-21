package com.campusrate.service;

import com.campusrate.dto.ReviewDtos.ReviewRequest;
import com.campusrate.dto.ReviewDtos.ReviewResponse;
import com.campusrate.dto.ReviewDtos.VoteResponse;
import com.campusrate.exception.ApiException;
import com.campusrate.model.IntlStats;
import com.campusrate.model.Review;
import com.campusrate.model.ReviewStats;
import com.campusrate.model.ReviewVote;
import com.campusrate.model.ReviewVoteId;
import com.campusrate.model.University;
import com.campusrate.model.User;
import com.campusrate.repository.ReviewRepository;
import com.campusrate.repository.ReviewVoteRepository;
import com.campusrate.repository.UniversityRepository;
import com.campusrate.repository.UserRepository;
import com.campusrate.text.SentimentAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests: the repositories and other services are replaced with Mockito mocks, so these
 * tests check only ReviewService's own logic and run in milliseconds without a database.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ReviewVoteRepository voteRepository;
    @Mock UniversityRepository universityRepository;
    @Mock UserRepository userRepository;
    @Mock UniversityService universityService;
    @Mock AuthService authService;
    @Mock SentimentAnalyzer sentimentAnalyzer;
    @Mock FraudDetector fraudDetector;
    @Mock PhotoService photoService;
    @Mock JdbcClient jdbc;

    @InjectMocks ReviewService reviewService;

    private User alice;
    private User bob;
    private University emu;

    @BeforeEach
    void setUp() {
        alice = new User("alice@emich.edu", "hash", "Alice");
        alice.setId(1L);
        bob = new User("bob@emich.edu", "hash", "Bob");
        bob.setId(2L);
        emu = new University("Eastern Michigan University", "Ypsilanti", "Michigan", "United States", null);
        emu.setId(10L);
    }

    /** The calls every successful create() makes. */
    private void stubSuccessfulCreate() {
        when(authService.findUser(1L)).thenReturn(alice);
        when(universityService.findUniversity(10L)).thenReturn(emu);
        when(reviewRepository.existsByUserIdAndUniversityId(1L, 10L)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(call -> {
            Review saved = call.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(reviewRepository.statsForUniversity(10L))
                .thenReturn(new ReviewStats(1L, 4.0, 5.0, 4.0, 3.0, 3.0, 5.0));
        when(reviewRepository.internationalStats(10L)).thenReturn(new IntlStats(0L, null));
    }

    @Test
    void createSavesReviewAndRefreshesSchoolAverages() {
        stubSuccessfulCreate();
        when(sentimentAnalyzer.score(any())).thenReturn(0.6);

        ReviewResponse response = reviewService.create(1L, request(5, 4, 3, 3, 5));

        assertThat(response.overall()).isEqualTo(4.0); // (5+4+3+3+5) / 5
        assertThat(response.mine()).isTrue();
        assertThat(response.status()).isEqualTo(Review.PUBLISHED);
        assertThat(emu.getReviewCount()).isEqualTo(1);
        assertThat(emu.getAvgAcademics()).isEqualTo(5.0);
        verify(universityRepository).save(emu);
    }

    @Test
    void suspiciousReviewIsSavedButFlagged() {
        stubSuccessfulCreate();
        List<FraudDetector.Flag> flags = List.of(new FraudDetector.Flag("BURST", "6 reviews in an hour"));
        when(fraudDetector.check(any(Review.class))).thenReturn(flags);

        ReviewResponse response = reviewService.create(1L, request(5, 5, 5, 5, 5));

        assertThat(response.status()).isEqualTo(Review.FLAGGED);
        verify(fraudDetector).saveFlags(eq(100L), anyList());
    }

    @Test
    void secondReviewOfSameSchoolIsRejected() {
        when(authService.findUser(1L)).thenReturn(alice);
        when(universityService.findUniversity(10L)).thenReturn(emu);
        when(reviewRepository.existsByUserIdAndUniversityId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(1L, request(5, 5, 5, 5, 5)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void internationalRatingsMustBeAllOrNothing() {
        ReviewRequest partial = new ReviewRequest(10L, 4, 4, 4, 4, 4, 5, null, null, null,
                "Long enough text for the review body to be valid here.", null, null, null);

        assertThatThrownBy(() -> reviewService.create(1L, partial))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("all four");
    }

    @Test
    void honeypotFieldBlocksBots() {
        ReviewRequest bot = new ReviewRequest(10L, 4, 4, 4, 4, 4, null, null, null, null,
                "Long enough text for the review body to be valid here.", null, null, "spam.example");

        assertThatThrownBy(() -> reviewService.create(1L, bot)).isInstanceOf(ApiException.class);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyTheAuthorCanEditAReview() {
        Review alicesReview = review(100L, alice);
        when(authService.findUser(2L)).thenReturn(bob);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(alicesReview));

        assertThatThrownBy(() -> reviewService.update(2L, 100L, request(1, 1, 1, 1, 1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cannotMarkOwnReviewHelpful() {
        when(authService.findUser(1L)).thenReturn(alice);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review(100L, alice)));

        assertThatThrownBy(() -> reviewService.toggleHelpful(1L, 100L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void helpfulVoteTogglesOnAndOffAndChangesReputation() {
        Review alicesReview = review(100L, alice);
        ReviewVoteId bobsVote = new ReviewVoteId(2L, 100L);
        when(authService.findUser(2L)).thenReturn(bob);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(alicesReview));

        when(voteRepository.existsById(bobsVote)).thenReturn(false);
        VoteResponse first = reviewService.toggleHelpful(2L, 100L);
        assertThat(first.voted()).isTrue();
        assertThat(first.helpfulCount()).isEqualTo(1);
        verify(voteRepository).save(any(ReviewVote.class));
        verify(userRepository).changeReputation(1L, 1);

        when(voteRepository.existsById(bobsVote)).thenReturn(true);
        VoteResponse second = reviewService.toggleHelpful(2L, 100L);
        assertThat(second.voted()).isFalse();
        assertThat(second.helpfulCount()).isZero();
        verify(voteRepository).deleteById(bobsVote);
        verify(userRepository).changeReputation(1L, -1);
    }

    @Test
    void unknownSortFallsBackToNewest() {
        assertThat(ReviewService.sortFor("nonsense").toString()).contains("createdAt: DESC");
        assertThat(ReviewService.sortFor("helpful").toString()).startsWith("helpfulCount: DESC");
    }

    private Review review(Long id, User author) {
        Review review = new Review(author, emu);
        review.setId(id);
        review.setRatings(4, 4, 4, 4, 4);
        review.setDetails("A long enough review body for the validation rules.", null, null);
        return review;
    }

    private static ReviewRequest request(int academics, int campusLife, int housing, int food, int safety) {
        return new ReviewRequest(10L, academics, campusLife, housing, food, safety,
                "Great professors and a friendly campus, but parking is hard to find.", "Computer Science", 2027);
    }
}
