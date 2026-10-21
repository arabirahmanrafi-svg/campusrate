package com.campusrate.text;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores how positive or negative a piece of text is, from -1 to +1.
 *
 * This is a "lexicon" approach: a list of positive and negative words, plus three rules
 * that handle the most common tricky cases:
 *   1. negation:   "not good"  counts as negative ("not" flips the next few words)
 *   2. intensity:  "very good" counts more than "good"
 *   3. "but":      in "the dorms are old but the people are great", the part after
 *                  "but" usually matters more, so it gets extra weight
 *
 * It's simple, fast, explainable and needs no outside service. It won't understand
 * sarcasm; a machine-learning model would do better, at the cost of all those benefits.
 */
@Component
public class SentimentAnalyzer {

    private static final Map<String, Double> LEXICON = Map.ofEntries(
            // positive
            Map.entry("good", 1.0), Map.entry("great", 1.5), Map.entry("excellent", 2.0), Map.entry("amazing", 2.0),
            Map.entry("awesome", 1.8), Map.entry("love", 1.6), Map.entry("loved", 1.6), Map.entry("best", 1.6),
            Map.entry("nice", 1.0), Map.entry("friendly", 1.2), Map.entry("helpful", 1.3), Map.entry("supportive", 1.4),
            Map.entry("clean", 1.0), Map.entry("safe", 1.0), Map.entry("affordable", 1.0), Map.entry("fun", 1.2),
            Map.entry("beautiful", 1.3), Map.entry("enjoy", 1.2), Map.entry("enjoyed", 1.2), Map.entry("happy", 1.3),
            Map.entry("recommend", 1.4), Map.entry("welcoming", 1.4), Map.entry("caring", 1.3), Map.entry("care", 0.8),
            Map.entry("easy", 0.8), Map.entry("convenient", 1.0), Map.entry("delicious", 1.6), Map.entry("fantastic", 1.8),
            Map.entry("wonderful", 1.8), Map.entry("strong", 0.8), Map.entry("quiet", 0.6), Map.entry("spacious", 1.0),
            Map.entry("comfortable", 1.0), Map.entry("diverse", 0.8), Map.entry("opportunities", 0.8),
            Map.entry("organized", 0.9), Map.entry("fair", 0.8), Map.entry("worth", 1.0), Map.entry("perfect", 1.8),
            Map.entry("impressive", 1.4), Map.entry("knowledgeable", 1.2), Map.entry("passionate", 1.2),
            Map.entry("accessible", 0.9), Map.entry("lively", 1.0), Map.entry("inclusive", 1.2),
            // negative
            Map.entry("bad", -1.0), Map.entry("terrible", -2.0), Map.entry("awful", -2.0), Map.entry("horrible", -2.0),
            Map.entry("worst", -2.0), Map.entry("hate", -1.6), Map.entry("hated", -1.6), Map.entry("poor", -1.2),
            Map.entry("dirty", -1.3), Map.entry("unsafe", -1.6), Map.entry("dangerous", -1.6), Map.entry("expensive", -1.0),
            Map.entry("overpriced", -1.3), Map.entry("boring", -1.1), Map.entry("rude", -1.5), Map.entry("unhelpful", -1.4),
            Map.entry("disorganized", -1.3), Map.entry("noisy", -1.0), Map.entry("loud", -0.8), Map.entry("crowded", -0.8),
            Map.entry("old", -0.5), Map.entry("broken", -1.2), Map.entry("difficult", -0.6), Map.entry("hard", -0.4),
            Map.entry("stressful", -1.2), Map.entry("disappointing", -1.4), Map.entry("disappointed", -1.4),
            Map.entry("lacking", -1.0), Map.entry("lack", -0.9), Map.entry("slow", -0.8), Map.entry("bland", -1.0),
            Map.entry("repetitive", -0.9), Map.entry("small", -0.4), Map.entry("cramped", -1.1), Map.entry("unfair", -1.3),
            Map.entry("isolated", -1.0), Map.entry("lonely", -1.2), Map.entry("worse", -1.3), Map.entry("problem", -0.8),
            Map.entry("problems", -0.8), Map.entry("issue", -0.7), Map.entry("issues", -0.7), Map.entry("mold", -1.5),
            Map.entry("waste", -1.4), Map.entry("avoid", -1.4), Map.entry("confusing", -1.0), Map.entry("ignored", -1.3),
            Map.entry("racist", -2.0), Map.entry("unresponsive", -1.4), Map.entry("complain", -0.8));

    private static final Set<String> NEGATIONS = Set.of(
            "not", "no", "never", "don't", "doesn't", "didn't", "isn't", "wasn't", "aren't", "weren't",
            "can't", "cannot", "won't", "hardly", "barely", "nothing", "nobody");

    private static final Map<String, Double> INTENSIFIERS = Map.of(
            "very", 1.5, "really", 1.4, "extremely", 1.8, "super", 1.5, "so", 1.3,
            "incredibly", 1.8, "pretty", 1.1, "quite", 1.2, "somewhat", 0.7, "slightly", 0.6);

    /** How many following words a negation affects ("not very good" → "very" and "good"). */
    private static final int NEGATION_REACH = 3;

    public double score(String text) {
        List<String> words = TextUtils.words(text);
        double total = 0;
        int negateRemaining = 0;
        double intensity = 1.0;
        double clauseWeight = 1.0;

        for (String word : words) {
            if (word.equals("but") || word.equals("however") || word.equals("although")) {
                clauseWeight = 2.0; // what comes after "but" matters more
                negateRemaining = 0;
                continue;
            }
            if (NEGATIONS.contains(word)) {
                negateRemaining = NEGATION_REACH;
                continue;
            }
            Double boost = INTENSIFIERS.get(word);
            if (boost != null) {
                intensity = boost;
                continue;
            }

            Double value = LEXICON.get(word);
            if (value != null) {
                double contribution = value * intensity * clauseWeight;
                if (negateRemaining > 0) {
                    contribution = -contribution * 0.75; // "not great" is mildly bad, not as bad as "terrible"
                }
                total += contribution;
            }
            intensity = 1.0;
            if (negateRemaining > 0) {
                negateRemaining--;
            }
        }

        // Squash the total into -1..+1. Dividing by sqrt(total² + 15) means one strong
        // word gives a clear signal, and very long reviews can't run off to infinity.
        return total / Math.sqrt(total * total + 15);
    }
}
