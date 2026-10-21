package com.campusrate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String role = ROLE_STUDENT;

    /** CURRENT, ALUMNI, TRANSFER or PROSPECTIVE; shown as a badge on reviews. */
    @Column(name = "student_status", nullable = false)
    private String studentStatus = "CURRENT";

    @Column(name = "is_international", nullable = false)
    private boolean international;

    @Column(name = "home_country")
    private String homeCountry;

    /** Home country is private unless the student chooses to show it. */
    @Column(name = "show_country", nullable = false)
    private boolean showCountry;

    /**
     * Changed only by atomic SQL ("reputation = reputation + 1"), never by JPA, so two people
     * upvoting at the same moment can't overwrite each other's change.
     */
    @Column(nullable = false, insertable = false, updatable = false)
    private int reputation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
        // required by JPA
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void updateProfile(String displayName, String studentStatus, boolean international,
                              String homeCountry, boolean showCountry) {
        this.displayName = displayName;
        this.studentStatus = studentStatus;
        this.international = international;
        this.homeCountry = homeCountry;
        this.showCountry = showCountry;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /** The country to show on reviews, or null if the student keeps it private. */
    public String publicCountry() {
        return showCountry ? homeCountry : null;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public String getStudentStatus() { return studentStatus; }
    public boolean isInternational() { return international; }
    public String getHomeCountry() { return homeCountry; }
    public boolean isShowCountry() { return showCountry; }
    public int getReputation() { return reputation; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setRole(String role) { this.role = role; }

    /** Used by tests, which build objects without a database. */
    public void setId(Long id) { this.id = id; }

    /** Used by tests. */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
