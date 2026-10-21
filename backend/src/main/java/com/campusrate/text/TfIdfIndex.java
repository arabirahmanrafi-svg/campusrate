package com.campusrate.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the documents most similar to a query using TF-IDF and cosine similarity:
 * the classic search-engine technique, and the "retrieval" step of RAG.
 *
 *   TF  (term frequency):        how often a word appears in this document
 *   IDF (inverse document freq): log(N / documents containing the word); rare words
 *                                like "visa" count for much more than common ones like "campus"
 *
 * Each document becomes a vector of TF × IDF weights. Two texts are similar when their
 * vectors point the same way, measured by the cosine of the angle between them (0 to 1).
 *
 * A school has hundreds of review sentences, not millions, so building the index in
 * memory on each question takes a few milliseconds and needs no special database.
 *
 * @param <T> whatever each document refers back to (here: the review sentence and its review)
 */
public class TfIdfIndex<T> {

    public record Match<T>(T item, double score) {
    }

    private final List<T> items = new ArrayList<>();
    private final List<Map<String, Double>> vectors = new ArrayList<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final List<Map<String, Integer>> termCounts = new ArrayList<>();
    private boolean built;

    public void add(T item, String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String term : TextUtils.terms(text)) {
            counts.merge(term, 1, Integer::sum);
        }
        for (String term : counts.keySet()) {
            documentFrequency.merge(term, 1, Integer::sum);
        }
        items.add(item);
        termCounts.add(counts);
        built = false;
    }

    public int size() {
        return items.size();
    }

    /** Returns up to {@code limit} items scoring at least {@code minScore}, best first. */
    public List<Match<T>> search(String query, int limit, double minScore) {
        build();
        Map<String, Integer> queryCounts = new HashMap<>();
        for (String term : TextUtils.terms(query)) {
            queryCounts.merge(term, 1, Integer::sum);
        }
        Map<String, Double> queryVector = weigh(queryCounts);
        if (queryVector.isEmpty()) {
            return List.of();
        }

        List<Match<T>> matches = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            double score = cosine(queryVector, vectors.get(i));
            if (score >= minScore) {
                matches.add(new Match<>(items.get(i), score));
            }
        }
        matches.sort(Comparator.comparingDouble((Match<T> m) -> m.score()).reversed());
        return matches.size() > limit ? matches.subList(0, limit) : matches;
    }

    private void build() {
        if (built) {
            return;
        }
        vectors.clear();
        for (Map<String, Integer> counts : termCounts) {
            vectors.add(weigh(counts));
        }
        built = true;
    }

    private Map<String, Double> weigh(Map<String, Integer> counts) {
        Map<String, Double> vector = new HashMap<>();
        int n = items.size();
        counts.forEach((term, count) -> {
            Integer df = documentFrequency.get(term);
            if (df != null) { // words that appear in no document can't help ranking
                double idf = Math.log(1.0 + (double) n / df);
                vector.put(term, (1 + Math.log(count)) * idf);
            }
        });
        return vector;
    }

    static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            Double other = b.get(entry.getKey());
            if (other != null) {
                dot += entry.getValue() * other;
            }
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return normA == 0 || normB == 0 ? 0 : dot / (normA * normB);
    }
}
