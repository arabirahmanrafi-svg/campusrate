package com.campusrate.repository;

import com.campusrate.model.ReviewVote;
import com.campusrate.model.ReviewVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, ReviewVoteId> {

    /** Which of these reviews has this user already marked helpful? One query for a whole page. */
    @Query("""
            select v.id.reviewId from ReviewVote v
            where v.id.userId = :userId and v.id.reviewId in :reviewIds
            """)
    Set<Long> findVotedReviewIds(@Param("userId") Long userId,
                                 @Param("reviewIds") Collection<Long> reviewIds);
}
