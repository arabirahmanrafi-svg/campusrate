package com.campusrate.repository;

import com.campusrate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// Spring Data writes the SQL for most of these methods from their names.
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Adds (or removes) reputation points in one atomic SQL statement. Reading the value,
     * adding 1 in Java and saving it back could lose an update if two people vote at once.
     */
    @Modifying
    @Query(value = "update users set reputation = greatest(0, reputation + :delta) where id = :id", nativeQuery = true)
    int changeReputation(@Param("id") Long userId, @Param("delta") int delta);
}
