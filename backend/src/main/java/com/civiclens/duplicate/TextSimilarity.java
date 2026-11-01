package com.civiclens.duplicate;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic text normalization and similarity. Combines token-set Jaccard (word overlap) with character-trigram
 * Dice (tolerant to typos and word forms) so that "large pothole near market" and "big potholes near the market"
 * score as similar while unrelated reports do not.
 */
public final class TextSimilarity {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "there", "here", "this", "that", "these",
            "those", "it", "its", "of", "on", "in", "at", "to", "for", "from", "by", "with", "and", "or", "but", "near",
            "very", "so", "too", "has", "have", "had", "please", "our", "my", "we", "i", "you", "they", "them", "some",
            "any", "since", "days", "day", "still", "also", "just", "can", "will", "would", "should", "could", "shall", "do", "does", "did",
            "being", "into", "out", "up", "down", "over", "about", "after", "before", "again", "all", "lot", "lots");

    private TextSimilarity() {
    }

    /** Expands common negative contractions before punctuation removal, preserving meaning. */
    private static String expandNegativeContractions(String text) {
        String expanded = text.toLowerCase(Locale.ROOT)
                .replace("’", "'")
                .replace("‘", "'");

        return expanded
                .replaceAll("\\bwon't\\b", "will not")
                .replaceAll("\\bcan't\\b", "can not")
                .replaceAll("\\bshan't\\b", "shall not")
                .replaceAll("\\bain't\\b", "is not")
                .replaceAll("\\bisn't\\b", "is not")
                .replaceAll("\\baren't\\b", "are not")
                .replaceAll("\\bwasn't\\b", "was not")
                .replaceAll("\\bweren't\\b", "were not")
                .replaceAll("\\bdoesn't\\b", "does not")
                .replaceAll("\\bdon't\\b", "do not")
                .replaceAll("\\bdidn't\\b", "did not")
                .replaceAll("\\bhasn't\\b", "has not")
                .replaceAll("\\bhaven't\\b", "have not")
                .replaceAll("\\bhadn't\\b", "had not")
                .replaceAll("\\bcouldn't\\b", "could not")
                .replaceAll("\\bshouldn't\\b", "should not")
                .replaceAll("\\bwouldn't\\b", "would not")
                .replaceAll("\\bmustn't\\b", "must not")
                .replaceAll("\\bmightn't\\b", "might not");
    }

    /** Lower-cases, strips accents and punctuation, preserves negation, drops stopwords and stems lightly. */
    public static String normalize(String text) {
        if (text == null) return "";
        String folded = Normalizer.normalize(expandNegativeContractions(text), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (folded.isEmpty()) return "";
        return Arrays.stream(folded.split("\\s+"))
                .filter(t -> t.length() > 1 && !STOPWORDS.contains(t))
                .map(TextSimilarity::stem)
                .collect(Collectors.joining(" "));
    }

    static String stem(String token) {
        if (token.length() > 5 && token.endsWith("ing")) {
            return token.substring(0, token.length() - 3);
        }
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && token.endsWith("ed")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 4 && token.endsWith("es") && !token.endsWith("ses")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    /** Similarity of two already-normalized strings, in [0, 1]. */
    public static double similarity(String normalizedA, String normalizedB) {
        if (normalizedA == null || normalizedB == null || normalizedA.isBlank() || normalizedB.isBlank()) {
            return 0.0;
        }
        if (normalizedA.equals(normalizedB)) {
            return 1.0;
        }
        double jaccard = jaccard(tokens(normalizedA), tokens(normalizedB));
        double dice = dice(trigrams(normalizedA), trigrams(normalizedB));
        return Math.min(1.0, 0.5 * jaccard + 0.5 * dice);
    }

    /** Number of meaningful tokens; very short texts get less weight in duplicate scoring. */
    public static int tokenCount(String normalized) {
        return normalized == null || normalized.isBlank() ? 0 : normalized.split(" ").length;
    }

    static Set<String> tokens(String normalized) {
        return new LinkedHashSet<>(Arrays.asList(normalized.split(" ")));
    }

    static Set<String> trigrams(String normalized) {
        String padded = " " + normalized + " ";
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + 3 <= padded.length(); i++) {
            grams.add(padded.substring(i, i + 3));
        }
        return grams;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return 2.0 * inter.size() / (a.size() + b.size());
    }
}
