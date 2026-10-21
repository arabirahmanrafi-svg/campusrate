package com.campusrate.service;

import com.campusrate.model.University;
import com.campusrate.repository.UniversityRepository;
import com.campusrate.text.Similarity;
import com.campusrate.text.TextUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Typo-tolerant university search, in plain Java.
 *
 * Plain SQL "LIKE '%michgan%'" finds nothing when the user makes a typo. This keeps a small
 * in-memory list of every school's searchable words and scores each school against the query:
 *
 *   name contains the query ............................. best
 *   initials match ("mit", "ucla", "buet") .............. very good
 *   city contains the query ............................. good
 *   every query word matches a school word, exactly, as a
 *   prefix, or within 1-2 typos (Levenshtein distance) .. scored per word
 *
 * The list is rebuilt when the app starts and every 10 minutes. Thousands of schools fit
 * easily in memory and are scored in a millisecond or two; at a much larger scale this would
 * move to a dedicated search engine.
 */
@Service
public class SearchService {

    record Entry(Long id, String name, String nameLower, String cityLower, String initials,
                 List<String> words, int reviewCount) {
    }

    private final UniversityRepository universityRepository;
    private volatile List<Entry> entries = List.of(); // replaced as a whole, so readers never see half an update

    public SearchService(UniversityRepository universityRepository) {
        this.universityRepository = universityRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 10 * 60_000L, initialDelay = 10 * 60_000L)
    public void rebuild() {
        List<Entry> fresh = new ArrayList<>();
        for (University u : universityRepository.findAll()) {
            fresh.add(toEntry(u.getId(), u.getName(), u.getCity(), u.getState(), u.getCountry(), u.getReviewCount()));
        }
        entries = fresh;
    }

    static Entry toEntry(Long id, String name, String city, String state, String country, int reviewCount) {
        List<String> words = new ArrayList<>();
        words.addAll(TextUtils.words(name));
        words.addAll(TextUtils.words(city));
        if (state != null) words.addAll(TextUtils.words(state));
        words.addAll(TextUtils.words(country));
        return new Entry(id, name, name.toLowerCase(Locale.ROOT), city.toLowerCase(Locale.ROOT),
                initials(name), words, reviewCount);
    }

    /** "University of California, Los Angeles" → "ucla"; "Bangladesh University of Engineering and Technology" → "buet". */
    static String initials(String name) {
        StringBuilder result = new StringBuilder();
        for (String word : TextUtils.words(name)) {
            if (!word.equals("of") && !word.equals("the") && !word.equals("and") && !word.equals("at")) {
                result.append(word.charAt(0));
            }
        }
        return result.toString();
    }

    /** Returns the ids of the best matches, best first. */
    public List<Long> search(String query, int limit) {
        return rank(entries, query, limit);
    }

    static List<Long> rank(List<Entry> entries, String query, int limit) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> queryWords = TextUtils.words(q).stream()
                .filter(w -> !TextUtils.STOP_WORDS.contains(w))
                .toList();
        if (q.length() < 2) {
            return List.of();
        }

        record Scored(Entry entry, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Entry e : entries) {
            double score = score(e, q, queryWords);
            if (score > 0) {
                scored.add(new Scored(e, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.entry().reviewCount(), Comparator.reverseOrder())
                .thenComparing(s -> s.entry().name()));
        return scored.stream().limit(limit).map(s -> s.entry().id()).toList();
    }

    static double score(Entry e, String q, List<String> queryWords) {
        if (e.nameLower().contains(q)) {
            return 100 + (e.nameLower().startsWith(q) ? 20 : 0);
        }
        if (q.length() >= 2 && !q.contains(" ") && e.initials().equals(q)) {
            return 90;
        }
        if (e.cityLower().contains(q)) {
            return 80;
        }
        if (queryWords.isEmpty()) {
            return 0;
        }

        // Every query word must match some word of the school.
        double total = 0;
        for (String qw : queryWords) {
            double best = 0;
            for (String w : e.words()) {
                best = Math.max(best, wordScore(qw, w));
            }
            if (best == 0) {
                return 0;
            }
            total += best;
        }
        return total / queryWords.size() * 6; // scaled to sit below the exact matches above
    }

    /** 10 = same word, 8 = prefix ("mich" → "michigan"), 6 or 4 = one or two typos, 0 = no match. */
    static double wordScore(String queryWord, String word) {
        if (word.equals(queryWord)) return 10;
        if (queryWord.length() >= 2 && word.startsWith(queryWord)) return 8;
        int allowed = queryWord.length() >= 7 ? 2 : queryWord.length() >= 4 ? 1 : 0;
        if (allowed == 0) return 0;

        // Compare against the whole word and against a same-length prefix, so a typo
        // in a half-typed word ("michg" → "michigan") still matches.
        int distance = Similarity.editDistance(queryWord, word, allowed);
        if (word.length() > queryWord.length()) {
            distance = Math.min(distance,
                    Similarity.editDistance(queryWord, word.substring(0, queryWord.length()), allowed));
        }
        return distance <= allowed ? 6 - (distance - 1) * 2 : 0;
    }
}
