package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.PhotoResponse;
import com.campusrate.dto.ReviewDtos.PageResponse;
import com.campusrate.dto.ReviewDtos.ReviewRequest;
import com.campusrate.dto.ReviewDtos.ReviewResponse;
import com.campusrate.dto.ReviewDtos.VoteResponse;
import com.campusrate.exception.ApiException;
import com.campusrate.model.IntlStats;
import com.campusrate.model.Review;
import com.campusrate.model.ReviewVote;
import com.campusrate.model.ReviewVoteId;
import com.campusrate.model.University;
import com.campusrate.model.User;
import com.campusrate.repository.ReviewRepository;
import com.campusrate.repository.ReviewVoteRepository;
import com.campusrate.repository.UniversityRepository;
import com.campusrate.repository.UserRepository;
import com.campusrate.text.SentimentAnalyzer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReviewService {

    private static final int PAGE_SIZE = 10;

    private final ReviewRepository reviewRepository;
    private final ReviewVoteRepository voteRepository;
    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final UniversityService universityService;
    private final AuthService authService;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final FraudDetector fraudDetector;
    private final PhotoService photoService;
    private final JdbcClient jdbc;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewVoteRepository voteRepository,
                         UniversityRepository universityRepository,
                         UserRepository userRepository,
                         UniversityService universityService,
                         AuthService authService,
                         SentimentAnalyzer sentimentAnalyzer,
                         FraudDetector fraudDetector,
                         PhotoService photoService,
                         JdbcClient jdbc) {
        this.reviewRepository = reviewRepository;
        this.voteRepository = voteRepository;
        this.universityRepository = universityRepository;
        this.userRepository = userRepository;
        this.universityService = universityService;
        this.authService = authService;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.fraudDetector = fraudDetector;
        this.photoService = photoService;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ reading

    /**
     * One page of a school's published reviews. currentUserId is null for visitors who aren't logged in.
     * Optional filters: only reviews with international ratings, only reviewers from one country.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForUniversity(Long universityId, String sort, int page,
                                                          boolean internationalOnly, String country,
                                                          Long currentUserId) {
        universityService.findUniversity(universityId); // 404 if the school doesn't exist

        String countryFilter = country == null || country.isBlank() ? null : country.trim();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE, sortFor(sort));
        Page<Review> reviews = reviewRepository.findPublished(universityId, internationalOnly, countryFilter, pageRequest);

        List<Long> ids = reviews.map(Review::getId).toList();
        // Look up votes and photos for the whole page in one query each, not one query per review.
        Set<Long> votedIds = currentUserId == null || ids.isEmpty()
                ? Set.of()
                : voteRepository.findVotedReviewIds(currentUserId, ids);
        Map<Long, List<PhotoResponse>> photos = photoService.approvedForReviews(ids);

        List<ReviewResponse> content = reviews.stream()
                .map(r -> ReviewResponse.from(r, false,
                        Objects.equals(r.getUser().getId(), currentUserId),
                        votedIds.contains(r.getId()),
                        photos.getOrDefault(r.getId(), List.of())))
                .toList();

        return new PageResponse<>(content, reviews.getNumber(), reviews.getTotalPages(), reviews.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> myReviews(Long userId) {
        User user = authService.findUser(userId);
        List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        Map<Long, List<PhotoResponse>> photos = photoService.approvedForReviews(reviews.stream().map(Review::getId).toList());
        return reviews.stream()
                .map(r -> ReviewResponse.from(r, true, true, false, photos.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    static Sort sortFor(String sort) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort == null) {
            return newestFirst;
        }
        return switch (sort) {
            case "helpful" -> Sort.by(Sort.Direction.DESC, "helpfulCount").and(newestFirst);
            case "highest" -> Sort.by(Sort.Direction.DESC, "overall").and(newestFirst);
            case "lowest" -> Sort.by(Sort.Direction.ASC, "overall").and(newestFirst);
            default -> newestFirst;
        };
    }

    // ------------------------------------------------------------------ writing

    /**
     * Saves a review AND updates the school's averages in one transaction.
     * If anything fails, both changes are rolled back together, so the stored
     * averages can never disagree with the actual reviews.
     *
     * Every new review also goes through the fake-review rules; a flagged review is
     * saved but hidden (and left out of the averages) until a moderator checks it.
     */
    @Transactional
    @CacheEvict(cacheNames = {"rankings", "universityDetail", "insights"}, allEntries = true)
    public ReviewResponse create(Long userId, ReviewRequest request) {
        rejectBots(request);
        validateInternationalRatings(request);

        User user = authService.findUser(userId);
        University university = universityService.findUniversity(request.universityId());

        // Friendly check first. The UNIQUE key in the database is the real guarantee
        // (it also catches two requests arriving at the exact same time).
        if (reviewRepository.existsByUserIdAndUniversityId(user.getId(), university.getId())) {
            throw ApiException.conflict("You've already reviewed this university. You can edit your review instead.");
        }

        Review review = new Review(user, university);
        applyRequest(review, request);
        review = reviewRepository.saveAndFlush(review);

        List<FraudDetector.Flag> flags = fraudDetector.check(review);
        if (!flags.isEmpty()) {
            review.setStatus(Review.FLAGGED);
            fraudDetector.saveFlags(review.getId(), flags);
        }

        refreshUniversityStats(university);
        return ReviewResponse.from(review, true, true, false, List.of());
    }

    @Transactional
    @CacheEvict(cacheNames = {"rankings", "universityDetail", "insights"}, allEntries = true)
    public ReviewResponse update(Long userId, Long reviewId, ReviewRequest request) {
        rejectBots(request);
        validateInternationalRatings(request);

        Review review = findOwnReview(userId, reviewId);
        if (Review.REMOVED.equals(review.getStatus())) {
            throw ApiException.forbidden("This review was removed by a moderator and can't be edited.");
        }
        applyRequest(review, request);

        refreshUniversityStats(review.getUniversity());
        return ReviewResponse.from(review, true, true, false,
                photoService.approvedForReviews(List.of(reviewId)).getOrDefault(reviewId, List.of()));
    }

    @Transactional
    @CacheEvict(cacheNames = {"rankings", "universityDetail", "insights"}, allEntries = true)
    public void delete(Long userId, Long reviewId) {
        Review review = findOwnReview(userId, reviewId);
        University university = review.getUniversity();

        reviewRepository.delete(review); // its votes, flags and photos are removed by ON DELETE CASCADE
        refreshUniversityStats(university);
    }

    /** Marks a review helpful, or removes the mark if the user already voted (a toggle). */
    @Transactional
    public VoteResponse toggleHelpful(Long userId, Long reviewId) {
        User user = authService.findUser(userId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found."));

        if (review.getUser().getId().equals(user.getId())) {
            throw ApiException.badRequest("You can't mark your own review as helpful.");
        }

        ReviewVoteId voteId = new ReviewVoteId(user.getId(), reviewId);
        boolean nowVoted;
        if (voteRepository.existsById(voteId)) {
            voteRepository.deleteById(voteId);
            review.decrementHelpful();
            nowVoted = false;
        } else {
            voteRepository.save(new ReviewVote(user.getId(), reviewId));
            review.incrementHelpful();
            nowVoted = true;
        }
        // Reputation: the review's author gains a point when someone finds it helpful.
        userRepository.changeReputation(review.getUser().getId(), nowVoted ? 1 : -1);
        return new VoteResponse(nowVoted, review.getHelpfulCount());
    }

    // ------------------------------------------------------------------ moderation (called by AdminService)

    @Transactional
    @CacheEvict(cacheNames = {"rankings", "universityDetail", "insights"}, allEntries = true)
    public void setStatusByModerator(Long reviewId, String status) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found."));
        review.setStatus(status);
        jdbc.sql("delete from review_flags where review_id = :id").param("id", reviewId).update();
        refreshUniversityStats(review.getUniversity());
    }

    // ------------------------------------------------------------------ helpers

    private static void rejectBots(ReviewRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            throw ApiException.badRequest("Your review couldn't be saved. Please try again.");
        }
    }

    private static void validateInternationalRatings(ReviewRequest request) {
        if (request.hasAnyInternationalRating() && !request.hasAllInternationalRatings()) {
            throw ApiException.badRequest("Please fill in all four international student ratings, or leave them all empty.");
        }
    }

    private Review findOwnReview(Long userId, Long reviewId) {
        User user = authService.findUser(userId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found."));
        if (!review.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You can only change your own reviews.");
        }
        return review;
    }

    private void applyRequest(Review review, ReviewRequest request) {
        review.setRatings(request.academics(), request.campusLife(), request.housing(),
                request.food(), request.safety());
        review.setInternationalRatings(request.intlSupport(), request.visaHelp(),
                request.jobAvailability(), request.welcoming());
        String major = request.major() == null || request.major().isBlank() ? null : request.major().trim();
        String body = request.body().trim();
        review.setDetails(body, major, request.gradYear());
        review.setSentiment(sentimentAnalyzer.score(body));
    }

    /** Recalculates a school's stored averages from its published reviews. */
    void refreshUniversityStats(University university) {
        reviewRepository.flush(); // make sure the change we just made is visible to the queries
        university.applyStats(reviewRepository.statsForUniversity(university.getId()));

        IntlStats intl = reviewRepository.internationalStats(university.getId());
        university.applyInternationalStats(intl.count().intValue(), intl.average());

        universityRepository.save(university);
    }
}
