package com.campusrate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key (user_id, review_id) for review_votes. */
@Embeddable
public class ReviewVoteId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "review_id")
    private Long reviewId;

    protected ReviewVoteId() {
        // required by JPA
    }

    public ReviewVoteId(Long userId, Long reviewId) {
        this.userId = userId;
        this.reviewId = reviewId;
    }

    public Long getUserId() { return userId; }
    public Long getReviewId() { return reviewId; }

    // JPA compares keys with equals/hashCode, so both must use every field.
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReviewVoteId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(reviewId, that.reviewId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, reviewId);
    }
}
