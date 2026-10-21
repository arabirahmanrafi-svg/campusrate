package com.campusrate.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A "this review was helpful" vote. One per user per review (see ReviewVoteId). */
@Entity
@Table(name = "review_votes")
public class ReviewVote {

    @EmbeddedId
    private ReviewVoteId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReviewVote() {
        // required by JPA
    }

    public ReviewVote(Long userId, Long reviewId) {
        this.id = new ReviewVoteId(userId, reviewId);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ReviewVoteId getId() { return id; }
}
