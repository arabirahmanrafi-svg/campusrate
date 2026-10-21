package com.campusrate.model;

/** How many reviews include international ratings, and their average (see ReviewRepository.internationalStats). */
public record IntlStats(Long count, Double average) {
    public IntlStats {
        count = count == null ? 0L : count;
        average = average == null ? 0.0 : average;
    }
}
