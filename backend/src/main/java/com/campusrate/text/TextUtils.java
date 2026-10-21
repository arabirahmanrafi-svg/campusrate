package com.campusrate.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Small text helpers shared by the sentiment, search and insight features. */
public final class TextUtils {

    private TextUtils() {
    }

    /** Very common words that carry no meaning on their own ("the", "and", ...). */
    public static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "for", "with", "by",
            "from", "as", "is", "are", "was", "were", "be", "been", "being", "it", "its", "this", "that",
            "these", "those", "i", "me", "my", "we", "our", "you", "your", "they", "them", "their", "he",
            "she", "his", "her", "there", "here", "so", "do", "does", "did", "have", "has", "had", "will",
            "would", "can", "could", "should", "just", "about", "into", "than", "then", "also", "what",
            "which", "who", "how", "when", "where", "why", "all", "any", "some", "more", "most", "very",
            "really", "much", "many", "lot", "lots", "get", "got", "one", "s", "t");

    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z0-9']+");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+|\\n+");

    /** Lowercase words, keeping apostrophes so "don't" stays one word. */
    public static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String word : NON_LETTERS.split(text.toLowerCase(Locale.ROOT))) {
            String trimmed = word.replaceAll("^'+|'+$", "");
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** Words useful for matching: no stop words, and a simple stem so "dorms" matches "dorm". */
    public static List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        for (String word : words(text)) {
            if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                result.add(stem(word));
            }
        }
        return result;
    }

    /**
     * A deliberately simple stemmer: strips a few common English endings.
     * "parking" → "park", "classes" → "class", "professors" → "professor".
     */
    public static String stem(String word) {
        if (word.length() > 5 && word.endsWith("ing")) return word.substring(0, word.length() - 3);
        if (word.length() > 4 && word.endsWith("ies")) return word.substring(0, word.length() - 3) + "y";
        if (word.length() > 4 && (word.endsWith("ses") || word.endsWith("xes") || word.endsWith("ches"))) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }

    /** Splits a review into sentences. */
    public static List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String part : SENTENCE_END.split(text.trim())) {
            String sentence = part.trim();
            if (sentence.length() >= 15) { // skip fragments like "Overall." or "Pros:"
                result.add(sentence);
            }
        }
        return result;
    }
}
