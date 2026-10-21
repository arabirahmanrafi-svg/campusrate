package com.campusrate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "universities")
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private String state;

    @Column(nullable = false)
    private String country;

    private String website;

    private Double latitude;

    private Double longitude;

    // ----- Stored aggregates (kept in sync by ReviewService) -----

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "avg_overall", nullable = false)
    private double avgOverall;

    @Column(name = "avg_academics", nullable = false)
    private double avgAcademics;

    @Column(name = "avg_campus_life", nullable = false)
    private double avgCampusLife;

    @Column(name = "avg_housing", nullable = false)
    private double avgHousing;

    @Column(name = "avg_food", nullable = false)
    private double avgFood;

    @Column(name = "avg_safety", nullable = false)
    private double avgSafety;

    @Column(name = "avg_international", nullable = false)
    private double avgInternational;

    @Column(name = "intl_review_count", nullable = false)
    private int intlReviewCount;

    /** Change in average rating over the last 30 days, updated every hour by TrendingService. */
    @Column(name = "trend_delta", nullable = false, insertable = false, updatable = false)
    private double trendDelta;

    protected University() {
        // required by JPA
    }

    public University(String name, String city, String state, String country, String website) {
        this.name = name;
        this.city = city;
        this.state = state;
        this.country = country;
        this.website = website;
    }

    /** Replaces the stored averages with freshly calculated ones. */
    public void applyStats(ReviewStats stats) {
        this.reviewCount = stats.count().intValue();
        this.avgOverall = stats.overall();
        this.avgAcademics = stats.academics();
        this.avgCampusLife = stats.campusLife();
        this.avgHousing = stats.housing();
        this.avgFood = stats.food();
        this.avgSafety = stats.safety();
    }

    public void applyInternationalStats(int count, double average) {
        this.intlReviewCount = count;
        this.avgInternational = average;
    }

    /** Returns the stored average for a ranking category. */
    public double averageFor(Category category) {
        return switch (category) {
            case OVERALL -> avgOverall;
            case ACADEMICS -> avgAcademics;
            case CAMPUS_LIFE -> avgCampusLife;
            case HOUSING -> avgHousing;
            case FOOD -> avgFood;
            case SAFETY -> avgSafety;
            case INTERNATIONAL -> avgInternational;
        };
    }

    /** How many reviews count toward a category (international ratings are optional). */
    public int reviewCountFor(Category category) {
        return category == Category.INTERNATIONAL ? intlReviewCount : reviewCount;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getWebsite() { return website; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public int getReviewCount() { return reviewCount; }
    public double getAvgOverall() { return avgOverall; }
    public double getAvgAcademics() { return avgAcademics; }
    public double getAvgCampusLife() { return avgCampusLife; }
    public double getAvgHousing() { return avgHousing; }
    public double getAvgFood() { return avgFood; }
    public double getAvgSafety() { return avgSafety; }
    public double getAvgInternational() { return avgInternational; }
    public int getIntlReviewCount() { return intlReviewCount; }
    public double getTrendDelta() { return trendDelta; }

    /** Used by tests, which build objects without a database. */
    public void setId(Long id) { this.id = id; }
}
