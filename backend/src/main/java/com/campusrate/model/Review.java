package com.campusrate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {

    public static final String PUBLISHED = "PUBLISHED";
    public static final String FLAGGED = "FLAGGED";
    public static final String REMOVED = "REMOVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: the user and university are only loaded from the database if the code actually uses them.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id")
    private University university;

    @Column(nullable = false)
    private int academics;

    @Column(name = "campus_life", nullable = false)
    private int campusLife;

    @Column(nullable = false)
    private int housing;

    @Column(nullable = false)
    private int food;

    @Column(nullable = false)
    private int safety;

    @Column(nullable = false)
    private double overall;

    // International student ratings: all four are set together, or all are null.
    @Column(name = "intl_support")
    private Integer intlSupport;

    @Column(name = "visa_help")
    private Integer visaHelp;

    @Column(name = "job_availability")
    private Integer jobAvailability;

    @Column(name = "welcoming")
    private Integer welcoming;

    @Column(nullable = false, length = 2000)
    private String body;

    private String major;

    @Column(name = "grad_year")
    private Integer gradYear;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    /** -1 (negative) to +1 (positive), computed from the text when the review is saved. */
    @Column(nullable = false)
    private double sentiment;

    /** PUBLISHED, FLAGGED (hidden until a moderator checks it) or REMOVED. */
    @Column(nullable = false)
    private String status = PUBLISHED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Review() {
        // required by JPA
    }

    public Review(User user, University university) {
        this.user = user;
        this.university = university;
    }

    /** Sets all ratings at once and recalculates the overall score. */
    public void setRatings(int academics, int campusLife, int housing, int food, int safety) {
        this.academics = academics;
        this.campusLife = campusLife;
        this.housing = housing;
        this.food = food;
        this.safety = safety;
        this.overall = (academics + campusLife + housing + food + safety) / 5.0;
    }

    public void setInternationalRatings(Integer intlSupport, Integer visaHelp, Integer jobAvailability, Integer welcoming) {
        this.intlSupport = intlSupport;
        this.visaHelp = visaHelp;
        this.jobAvailability = jobAvailability;
        this.welcoming = welcoming;
    }

    public void setDetails(String body, String major, Integer gradYear) {
        this.body = body;
        this.major = major;
        this.gradYear = gradYear;
    }

    public void setSentiment(double sentiment) { this.sentiment = sentiment; }

    public void setStatus(String status) { this.status = status; }

    public void incrementHelpful() { helpfulCount++; }

    public void decrementHelpful() { helpfulCount = Math.max(0, helpfulCount - 1); }

    /** Average of the four international ratings, or null if the reviewer didn't give them. */
    public Double internationalScore() {
        if (intlSupport == null) {
            return null;
        }
        return (intlSupport + visaHelp + jobAvailability + welcoming) / 4.0;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public University getUniversity() { return university; }
    public int getAcademics() { return academics; }
    public int getCampusLife() { return campusLife; }
    public int getHousing() { return housing; }
    public int getFood() { return food; }
    public int getSafety() { return safety; }
    public double getOverall() { return overall; }
    public Integer getIntlSupport() { return intlSupport; }
    public Integer getVisaHelp() { return visaHelp; }
    public Integer getJobAvailability() { return jobAvailability; }
    public Integer getWelcoming() { return welcoming; }
    public String getBody() { return body; }
    public String getMajor() { return major; }
    public Integer getGradYear() { return gradYear; }
    public int getHelpfulCount() { return helpfulCount; }
    public double getSentiment() { return sentiment; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** Used by tests, which build objects without a database. */
    public void setId(Long id) { this.id = id; }

    /** Used by tests. */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
