package com.campusrate.model;

import java.util.List;

/**
 * The kinds of places students can rate inside a university.
 *
 * Each type has its own rating questions (a dorm has "Quietness", a job has "Pay") and
 * extra details (a dining review can name the meal). Keeping that in one enum means the
 * backend validation and the React form both come from the same definition: React asks
 * GET /api/place-types and builds the form from it, so adding a new place type is a
 * change in this file only.
 */
public enum PlaceType {

    DORM("Dorms", "dorm",
            List.of(new RatingField("cleanliness", "Cleanliness"),
                    new RatingField("quietness", "Quietness"),
                    new RatingField("roomSize", "Room size"),
                    new RatingField("staff", "Staff and RAs"),
                    new RatingField("value", "Value for money")),
            List.of(new ExtraField("roomType", "Room type (e.g. double, suite)", "text"),
                    new ExtraField("minutesToClass", "Minutes walk to most classes", "number"))),

    DINING("Dining", "dining hall",
            List.of(new RatingField("taste", "Taste"),
                    new RatingField("variety", "Variety"),
                    new RatingField("portions", "Portions"),
                    new RatingField("value", "Value for money")),
            List.of(new ExtraField("meal", "What did you eat?", "text"),
                    new ExtraField("dietary", "Good options for (e.g. halal, vegetarian)", "text"))),

    COURSE("Courses", "course",
            List.of(new RatingField("teaching", "Teaching quality"),
                    new RatingField("usefulness", "Usefulness"),
                    new RatingField("organization", "Organization"),
                    new RatingField("easiness", "Easiness")),
            List.of(new ExtraField("hoursPerWeek", "Hours of work per week", "number"),
                    new ExtraField("attendanceRequired", "Attendance required?", "yesno"),
                    new ExtraField("semester", "Semester taken (e.g. Fall 2026)", "text"))),

    JOB("Campus jobs", "campus job",
            List.of(new RatingField("pay", "Pay"),
                    new RatingField("flexibility", "Schedule flexibility"),
                    new RatingField("management", "Managers"),
                    new RatingField("workload", "Manageable workload")),
            List.of(new ExtraField("hourlyPay", "Hourly pay (USD)", "number"),
                    new ExtraField("hoursPerWeek", "Hours per week", "number"))),

    CLUB("Clubs", "club",
            List.of(new RatingField("community", "Community"),
                    new RatingField("activity", "How active it is"),
                    new RatingField("organization", "Organization")),
            List.of(new ExtraField("hoursPerWeek", "Hours per week", "number"),
                    new ExtraField("meets", "How often it meets", "text")));

    /** A 1-5 star question. */
    public record RatingField(String key, String label) {
    }

    /** An extra detail. kind is "text", "number" or "yesno". */
    public record ExtraField(String key, String label, String kind) {
    }

    private final String label;
    private final String singular;
    private final List<RatingField> ratings;
    private final List<ExtraField> extras;

    PlaceType(String label, String singular, List<RatingField> ratings, List<ExtraField> extras) {
        this.label = label;
        this.singular = singular;
        this.ratings = ratings;
        this.extras = extras;
    }

    public String getLabel() { return label; }
    public String getSingular() { return singular; }
    public List<RatingField> getRatings() { return ratings; }
    public List<ExtraField> getExtras() { return extras; }

    public boolean hasRating(String key) {
        return ratings.stream().anyMatch(r -> r.key().equals(key));
    }

    public boolean hasExtra(String key) {
        return extras.stream().anyMatch(e -> e.key().equals(key));
    }
}
