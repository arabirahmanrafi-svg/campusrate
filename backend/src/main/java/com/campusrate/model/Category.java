package com.campusrate.model;

/** The rating categories students score, plus the overall average. Used for rankings. */
public enum Category {
    OVERALL,
    ACADEMICS,
    CAMPUS_LIFE,
    HOUSING,
    FOOD,
    SAFETY,
    /** Average of the four international-student ratings (only from reviewers who gave them). */
    INTERNATIONAL;

    /** Accepts "overall", "campus_life", "campus-life", "CAMPUS_LIFE" and so on. */
    public static Category fromParam(String value) {
        if (value == null || value.isBlank()) {
            return OVERALL;
        }
        return Category.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
