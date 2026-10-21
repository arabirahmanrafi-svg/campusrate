package com.campusrate.model;

/**
 * Averages for one university, calculated by the database in a single query
 * (see ReviewRepository.statsForUniversity).
 *
 * The database returns null averages when a school has no reviews, so the
 * compact constructor turns those into 0.
 */
public record ReviewStats(
        Long count,
        Double overall,
        Double academics,
        Double campusLife,
        Double housing,
        Double food,
        Double safety
) {
    public ReviewStats {
        count = count == null ? 0L : count;
        overall = orZero(overall);
        academics = orZero(academics);
        campusLife = orZero(campusLife);
        housing = orZero(housing);
        food = orZero(food);
        safety = orZero(safety);
    }

    private static Double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
