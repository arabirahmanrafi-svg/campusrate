package com.campusrate.service;

import com.campusrate.dto.UniversityDtos;
import com.campusrate.dto.UniversityDtos.RankingEntry;
import com.campusrate.model.Category;
import com.campusrate.model.ReviewStats;
import com.campusrate.model.University;
import com.campusrate.repository.ReviewRepository;
import com.campusrate.repository.UniversityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks universities fairly using a Bayesian average.
 *
 * Problem: a school with 2 reviews of 5.0 would beat a school with 500 reviews of 4.7
 * if we sorted by plain average. That's not fair; 2 reviews prove very little.
 *
 * Fix: pretend every school starts with C "imaginary" reviews that all give the
 * site-wide average (m). Then:
 *
 *     score = (C * m + sum of the school's ratings) / (C + number of reviews)
 *
 * With few reviews the score stays close to m. As real reviews pile up, they
 * outweigh the imaginary ones and the score moves toward the school's own average.
 */
@Service
@Transactional(readOnly = true)
public class RankingService {

    private final UniversityRepository universityRepository;
    private final ReviewRepository reviewRepository;
    private final JdbcClient jdbc;
    private final double minReviewsWeight;

    public RankingService(UniversityRepository universityRepository,
                          ReviewRepository reviewRepository,
                          JdbcClient jdbc,
                          @Value("${app.ranking.min-reviews-weight}") double minReviewsWeight) {
        this.universityRepository = universityRepository;
        this.reviewRepository = reviewRepository;
        this.jdbc = jdbc;
        this.minReviewsWeight = minReviewsWeight;
    }

    /**
     * @param average     the school's own average rating
     * @param reviewCount how many reviews the school has
     * @param globalMean  the average rating across all schools (m)
     * @param weight      how many imaginary reviews to add (C)
     */
    public static double bayesianScore(double average, int reviewCount, double globalMean, double weight) {
        if (reviewCount <= 0) {
            return globalMean;
        }
        double sumOfRatings = average * reviewCount;
        return (weight * globalMean + sumOfRatings) / (weight + reviewCount);
    }

    /**
     * Cached: rankings are read far more often than reviews are written. ReviewService
     * clears the cache whenever a review changes, so the list is never out of date.
     */
    @Cacheable(cacheNames = "rankings", key = "#category.name() + '|' + #country + '|' + #limit")
    public List<RankingEntry> rankings(Category category, String country, int limit) {
        double globalMean = globalMean(category);

        List<University> schools = (country == null || country.isBlank())
                ? universityRepository.findByReviewCountGreaterThan(0)
                : universityRepository.findByReviewCountGreaterThanAndCountryIgnoreCase(0, country.trim());

        // Score every school, then sort: highest score first, more reviews breaks ties.
        record Scored(University school, double average, int count, double score) {
        }
        List<Scored> sorted = schools.stream()
                .filter(u -> u.reviewCountFor(category) > 0)
                .map(u -> {
                    double average = u.averageFor(category);
                    int count = u.reviewCountFor(category);
                    return new Scored(u, average, count,
                            bayesianScore(average, count, globalMean, minReviewsWeight));
                })
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(Scored::count, Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<RankingEntry> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Scored s = sorted.get(i);
            University u = s.school();
            result.add(new RankingEntry(i + 1, u.getId(), u.getName(), u.getCity(), u.getCountry(),
                    UniversityDtos.round(s.average()), s.count(), UniversityDtos.round(s.score())));
        }
        return result;
    }

    /** The site-wide average for a category (the "m" in the formula). */
    public double globalMean(Category category) {
        if (category == Category.INTERNATIONAL) {
            return jdbc.sql("""
                            select coalesce(avg((intl_support + visa_help + job_availability + welcoming) / 4), 0)
                            from reviews where status = 'PUBLISHED' and intl_support is not null
                            """)
                    .query(Double.class).single();
        }
        return averageFor(reviewRepository.statsForAllReviews(), category);
    }

    static double averageFor(ReviewStats stats, Category category) {
        return switch (category) {
            case OVERALL -> stats.overall();
            case ACADEMICS -> stats.academics();
            case CAMPUS_LIFE -> stats.campusLife();
            case HOUSING -> stats.housing();
            case FOOD -> stats.food();
            case SAFETY -> stats.safety();
            case INTERNATIONAL -> 0; // handled in globalMean
        };
    }

    /** Overall Bayesian score for one school, used by the fit matcher. */
    public double scoreFor(University university, Category category, double globalMean) {
        return bayesianScore(university.averageFor(category), university.reviewCountFor(category),
                globalMean, minReviewsWeight);
    }
}
