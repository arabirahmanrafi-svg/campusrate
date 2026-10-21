package com.campusrate.repository;

import com.campusrate.model.University;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UniversityRepository extends JpaRepository<University, Long> {

    /**
     * Search by name or city. Schools with more reviews come first, which usually
     * puts the school the student means at the top.
     */
    @Query("""
            select u from University u
            where lower(u.name) like lower(concat('%', :query, '%'))
               or lower(u.city) like lower(concat('%', :query, '%'))
            order by u.reviewCount desc, u.name asc
            """)
    List<University> search(@Param("query") String query, Pageable pageable);

    /** Schools that can appear in rankings (they have at least one review). */
    List<University> findByReviewCountGreaterThan(int minimumReviews);

    List<University> findByReviewCountGreaterThanAndCountryIgnoreCase(int minimumReviews, String country);

    @Query("select distinct u.country from University u order by u.country")
    List<String> findAllCountries();
}
