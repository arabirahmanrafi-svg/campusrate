package com.campusrate.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the plain-Java text features: sentiment, similarity and TF-IDF search. */
class TextFeaturesTest {

    private final SentimentAnalyzer sentiment = new SentimentAnalyzer();

    // ---------------------------------------------------------------- sentiment

    @Test
    void positiveAndNegativeTextScoreOnTheRightSide() {
        assertThat(sentiment.score("The professors are amazing and really helpful.")).isGreaterThan(0.3);
        assertThat(sentiment.score("The dorms are dirty and the staff is rude.")).isLessThan(-0.3);
        assertThat(sentiment.score("The library opens at 8.")).isEqualTo(0.0);
    }

    @Test
    void negationFlipsTheMeaning() {
        assertThat(sentiment.score("The food is not good.")).isLessThan(0);
        assertThat(sentiment.score("Not bad at all.")).isGreaterThan(0);
    }

    @Test
    void intensifiersMakeItStronger() {
        assertThat(sentiment.score("very good")).isGreaterThan(sentiment.score("good"));
    }

    @Test
    void theClauseAfterButCountsMore() {
        // Same words, different order: the part after "but" should win.
        assertThat(sentiment.score("The campus is beautiful but the food is terrible")).isLessThan(0);
        assertThat(sentiment.score("The food is terrible but the campus is beautiful")).isGreaterThan(0);
    }

    @Test
    void scoreStaysBetweenMinusOneAndOne() {
        String gushing = "amazing ".repeat(200);
        assertThat(sentiment.score(gushing)).isLessThan(1.0).isGreaterThan(0.9);
    }

    // ---------------------------------------------------------------- similarity

    @Test
    void jaccardOfShingles() {
        Set<String> a = Similarity.shingles("the food here is really great", 3);
        Set<String> sameText = Similarity.shingles("The food here is REALLY great!", 3);
        Set<String> different = Similarity.shingles("parking is impossible near the library", 3);

        assertThat(Similarity.jaccard(a, sameText)).isEqualTo(1.0);
        assertThat(Similarity.jaccard(a, different)).isEqualTo(0.0);
    }

    @Test
    void editDistanceCountsTypos() {
        assertThat(Similarity.editDistance("michgan", "michigan", 2)).isEqualTo(1);
        assertThat(Similarity.editDistance("toronto", "toronto", 2)).isZero();
        assertThat(Similarity.editDistance("kitten", "sitting", 5)).isEqualTo(3);
        // Stops early once the answer is bigger than the limit.
        assertThat(Similarity.editDistance("abcdef", "uvwxyz", 2)).isEqualTo(3);
    }

    // ---------------------------------------------------------------- text helpers and TF-IDF

    @Test
    void termsDropStopWordsAndStem() {
        assertThat(TextUtils.terms("The dorms are cleaner than the classes")).containsExactly("dorm", "cleaner", "class");
    }

    @Test
    void sentencesSplitOnPunctuationAndSkipFragments() {
        List<String> sentences = TextUtils.sentences("Great school overall. Ok. Parking is a nightmare though!");
        assertThat(sentences).containsExactly("Great school overall.", "Parking is a nightmare though!");
    }

    @Test
    void tfIdfFindsTheRelevantSentence() {
        TfIdfIndex<String> index = new TfIdfIndex<>();
        index.add("a", "The dining hall food is fresh and there are halal options.");
        index.add("b", "Parking near the library is very hard to find in the morning.");
        index.add("c", "Professors in computer science are great.");

        List<TfIdfIndex.Match<String>> matches = index.search("is parking hard?", 3, 0.05);

        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).item()).isEqualTo("b");
    }

    @Test
    void rareWordsCountMoreThanCommonOnes() {
        TfIdfIndex<String> index = new TfIdfIndex<>();
        index.add("common", "campus campus great campus");
        index.add("rare", "campus visa office helpful");
        index.add("other", "campus food okay");

        // "campus" appears everywhere, "visa" in one document: the visa document must win.
        assertThat(index.search("campus visa", 1, 0).get(0).item()).isEqualTo("rare");
    }
}
