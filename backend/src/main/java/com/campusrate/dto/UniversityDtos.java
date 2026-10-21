package com.campusrate.dto;

import com.campusrate.model.University;

import java.util.List;

/** What the API returns about universities. */
public final class UniversityDtos {

    private UniversityDtos() {
    }

    /** Short version, used in search results. */
    public record UniversitySummary(
            Long id, String name, String city, String state, String country,
            double avgOverall, int reviewCount
    ) {
        public static UniversitySummary from(University u) {
            return new UniversitySummary(u.getId(), u.getName(), u.getCity(), u.getState(),
                    u.getCountry(), round(u.getAvgOverall()), u.getReviewCount());
        }
    }

    /** Full version, used on a school's page and the compare page. */
    public record UniversityDetail(
            Long id, String name, String city, String state, String country, String website,
            Double latitude, Double longitude,
            int reviewCount, double avgOverall, double avgAcademics, double avgCampusLife,
            double avgHousing, double avgFood, double avgSafety,
            double avgInternational, int intlReviewCount, double trendDelta
    ) {
        public static UniversityDetail from(University u) {
            return new UniversityDetail(u.getId(), u.getName(), u.getCity(), u.getState(),
                    u.getCountry(), u.getWebsite(), u.getLatitude(), u.getLongitude(), u.getReviewCount(),
                    round(u.getAvgOverall()), round(u.getAvgAcademics()), round(u.getAvgCampusLife()),
                    round(u.getAvgHousing()), round(u.getAvgFood()), round(u.getAvgSafety()),
                    round(u.getAvgInternational()), u.getIntlReviewCount(), round(u.getTrendDelta()));
        }
    }

    /** One row on the rankings page. */
    public record RankingEntry(
            int rank, Long id, String name, String city, String country,
            double average, int reviewCount, double score
    ) {
    }

    /** A school whose rating is rising. */
    public record TrendingEntry(Long id, String name, String city, String country,
                                double avgOverall, int reviewCount, double trendDelta) {
    }

    /** One dot on the "worth the money" chart. */
    public record ValuePoint(Long id, String name, String country, double yearlyCost,
                             double avgOverall, int reviewCount, int costReports) {
    }

    // ---------- Find my fit ----------

    /** Quiz answers. Each weight is 0 (don't care) to 5 (very important). */
    public record FitRequest(
            Integer maxYearlyCost,
            String country,
            int academics, int campusLife, int housing, int food, int safety, int international
    ) {
    }

    public record FitResult(Long id, String name, String city, String country, int matchPercent,
                            List<String> strengths, Integer yearlyCost, int reviewCount) {
    }

    /** Rounds to 2 decimal places for display. */
    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
