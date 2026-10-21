package com.campusrate.text;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** String similarity helpers used for fake-review detection and typo-tolerant search. */
public final class Similarity {

    private Similarity() {
    }

    /**
     * Word "shingles": every run of {@code size} consecutive words.
     * "the food is great" with size 3 → {"the food is", "food is great"}.
     * Comparing shingles instead of single words catches copied phrases, not just shared vocabulary.
     */
    public static Set<String> shingles(String text, int size) {
        List<String> words = TextUtils.words(text);
        Set<String> result = new HashSet<>();
        if (words.size() < size) {
            if (!words.isEmpty()) {
                result.add(String.join(" ", words));
            }
            return result;
        }
        for (int i = 0; i + size <= words.size(); i++) {
            result.add(String.join(" ", words.subList(i, i + size)));
        }
        return result;
    }

    /** Jaccard similarity: shared items ÷ all distinct items. 1.0 = identical, 0.0 = nothing in common. */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        int union = a.size() + b.size() - intersection.size();
        return (double) intersection.size() / union;
    }

    /**
     * Levenshtein edit distance: the fewest single-letter inserts, deletes or substitutions
     * that turn one word into the other. "michgan" → "michigan" is 1.
     *
     * Dynamic programming with two rows, so it uses O(length) memory instead of O(length²).
     * Stops early (returns max + 1) once the distance must be larger than {@code max}.
     */
    public static int editDistance(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
