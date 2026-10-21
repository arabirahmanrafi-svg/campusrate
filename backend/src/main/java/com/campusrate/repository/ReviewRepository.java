package com.campusrate.repository;

import com.campusrate.model.IntlStats;
import com.campusrate.model.Review;
import com.campusrate.model.ReviewStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * One page of a school's published reviews, with optional filters:
     * only reviews that include international ratings, and/or only reviewers from a country.
     *
     * @EntityGraph loads each review's author in the same query, avoiding the
     * "N+1 queries" problem (one extra query per review).
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select r from Review r join r.user u
            where r.university.id = :universityId
              and r.status = 'PUBLISHED'
              and (:internationalOnly = false or r.intlSupport is not null)
              and (:country is null or (u.showCountry = true and u.homeCountry = :country))
            """)
    Page<Review> findPublished(@Param("universityId") Long universityId,
                               @Param("internationalOnly") boolean internationalOnly,
                               @Param("country") String country,
                               Pageable pageable);

    @EntityGraph(attributePaths = "university")
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** All published reviews of a school, used by the insights and "ask the reviews" features. */
    @Query("select r from Review r where r.university.id = :universityId and r.status = 'PUBLISHED' order by r.createdAt")
    List<Review> findAllPublished(@Param("universityId") Long universityId);

    boolean existsByUserIdAndUniversityId(Long userId, Long universityId);

    /** All averages for one school, calculated by the database in one query. Flagged reviews don't count. */
    @Query("""
            select new com.campusrate.model.ReviewStats(
                count(r), avg(r.overall), avg(r.academics), avg(r.campusLife),
                avg(r.housing), avg(r.food), avg(r.safety))
            from Review r
            where r.university.id = :universityId and r.status = 'PUBLISHED'
            """)
    ReviewStats statsForUniversity(@Param("universityId") Long universityId);

    /** International ratings for one school: only reviews that include them count. */
    @Query("""
            select new com.campusrate.model.IntlStats(
                count(r), avg((r.intlSupport + r.visaHelp + r.jobAvailability + r.welcoming) / 4.0))
            from Review r
            where r.university.id = :universityId and r.status = 'PUBLISHED' and r.intlSupport is not null
            """)
    IntlStats internationalStats(@Param("universityId") Long universityId);

    /** Averages across every published review on the site; the "m" in the Bayesian average. */
    @Query("""
            select new com.campusrate.model.ReviewStats(
                count(r), avg(r.overall), avg(r.academics), avg(r.campusLife),
                avg(r.housing), avg(r.food), avg(r.safety))
            from Review r
            where r.status = 'PUBLISHED'
            """)
    ReviewStats statsForAllReviews();
}
