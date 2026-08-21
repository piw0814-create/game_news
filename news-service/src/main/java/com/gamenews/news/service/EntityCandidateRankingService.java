package com.gamenews.news.service;

import com.gamenews.news.dto.EntityReviewDto;
import com.gamenews.news.entity.EntityReviewKind;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityCandidateRankingService {

    private static final double MIN_VISIBLE_FUZZY_SCORE = 0.40;
    private static final Pattern NUMERIC_RANGE_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])([ivxlcdm]+|\\d+)\\s*[-–—]\\s*([ivxlcdm]+|\\d+)(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern QUALIFIED_TAIL_PATTERN = Pattern.compile(
            "^(.+?:)\\s*(.+?)\\s+[-–—]\\s+(.+)$",
            Pattern.UNICODE_CASE);

    public List<EntityReviewDto.Candidate> rank(
            String detectedName,
            EntityReviewKind preferredKind,
            List<EntityReviewDto.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<ScoredCandidate> scored = new ArrayList<>();
        for (EntityReviewDto.Candidate candidate : candidates) {
            double identityScore = Math.max(
                    identityScore(detectedName, candidate.getName()),
                    identityScore(detectedName, candidate.getDisplayName()));

            boolean preferred = candidate.getEntityKind() == preferredKind;
            boolean localVerified = candidate.getLocalId() != null
                    && (candidate.getIgdbId() != null || candidate.getIgdbCollectionId() != null);
            boolean local = candidate.getLocalId() != null;
            boolean mainGame = candidate.getEntityKind() == EntityReviewKind.GAME
                    && candidate.getGameType() != null
                    && candidate.getGameType().equalsIgnoreCase("Main Game")
                    && candidate.getVersionParentIgdbId() == null;

            // Exact local identities must never disappear. Weak fuzzy IGDB suggestions are noise
            // (e.g. "The Finals" -> "Final Fantasy") and are omitted from admin review.
            if (!local && identityScore < MIN_VISIBLE_FUZZY_SCORE) {
                continue;
            }

            double sortScore = identityScore
                    + (preferred ? 0.02 : 0.0)
                    + (localVerified ? 0.01 : local ? 0.005 : 0.0)
                    + (mainGame ? 0.005 : 0.0);
            scored.add(new ScoredCandidate(candidate, sortScore, identityScore));
        }

        scored.sort(Comparator
                .comparingDouble(ScoredCandidate::sortScore).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::identityScore).reversed())
                .thenComparing(item -> safe(item.candidate().getName()), String.CASE_INSENSITIVE_ORDER));

        return scored.stream().map(ScoredCandidate::candidate).toList();
    }

    /**
     * AUTO resolution is deliberately stricter than review ranking.
     * It accepts only semantic-canonical equality or a complete leading-brand expansion.
     * A caller must still require this match to be unique among IGDB candidates.
     */
    boolean isSafeAutoEquivalent(String detectedName, String candidateName) {
        List<String> detected = canonicalTokens(detectedName);
        List<String> candidate = canonicalTokens(candidateName);
        if (detected.isEmpty() || candidate.isEmpty()) return false;
        return detected.equals(candidate) || isSafeLeadingExpansion(detected, candidate, 3);
    }

    boolean isCanonicalEquivalent(String leftName, String rightName) {
        List<String> left = canonicalTokens(leftName);
        List<String> right = canonicalTokens(rightName);
        return !left.isEmpty() && left.equals(right);
    }

    String standardEditionBaseName(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = normalizeUnicode(value).trim();
        String stripped = normalized.replaceFirst(
                "(?iu)\\s*[:\\-–—]?\\s*standard\\s+edition\\s*$", "").trim();
        return stripped.equals(normalized) || stripped.isBlank() ? null : stripped;
    }

    /**
     * Review-only fallback for AI names that concatenate a parent game's full subtitle with
     * a child expansion/DLC title, e.g.
     * "S.T.A.L.K.E.R. 2: Heart of Chornobyl – Cost of Hope"
     * -> "S.T.A.L.K.E.R. 2: Cost of Hope".
     *
     * This method only creates an alternate lookup key. Callers must not AUTO_LINK from this
     * fallback; it exists to turn a zero-candidate review into an admin-verifiable candidate.
     */
    String collapsedQualifiedTailName(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = normalizeUnicode(value).trim();
        Matcher matcher = QUALIFIED_TAIL_PATTERN.matcher(normalized);
        if (!matcher.matches()) return null;

        String prefix = matcher.group(1).trim();
        String middle = matcher.group(2).trim();
        String tail = matcher.group(3).trim();
        if (prefix.isBlank() || middle.isBlank() || tail.isBlank()) return null;

        String collapsed = (prefix + " " + tail).trim();
        return collapsed.equalsIgnoreCase(normalized) ? null : collapsed;
    }

    double identityScore(String detectedName, String candidateName) {
        String left = canonicalCompact(detectedName);
        String right = canonicalCompact(candidateName);
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        if (left.equals(right)) return 1.0;

        String leftWithoutArticle = stripLeadingArticle(canonicalWords(detectedName));
        String rightWithoutArticle = stripLeadingArticle(canonicalWords(candidateName));
        if (!leftWithoutArticle.isEmpty() && leftWithoutArticle.equals(rightWithoutArticle)) {
            return 0.97;
        }

        double score = levenshteinSimilarity(left, right) * 0.70;

        int shorter = Math.min(left.length(), right.length());
        int longer = Math.max(left.length(), right.length());
        if (shorter >= 4 && (left.contains(right) || right.contains(left))) {
            double lengthRatio = (double) shorter / longer;
            score = Math.max(score, 0.65 + (0.20 * lengthRatio));
        }

        List<String> leftTokenList = canonicalTokens(detectedName);
        List<String> rightTokenList = canonicalTokens(candidateName);
        Set<String> leftTokens = new HashSet<>(leftTokenList);
        Set<String> rightTokens = new HashSet<>(rightTokenList);
        if (!leftTokens.isEmpty() && !rightTokens.isEmpty()) {
            Set<String> intersection = new HashSet<>(leftTokens);
            intersection.retainAll(rightTokens);
            if (!intersection.isEmpty()) {
                double dice = (2.0 * intersection.size()) / (leftTokens.size() + rightTokens.size());
                score = Math.max(score, 0.45 + (0.35 * dice));
            }
        }

        if (isSafeLeadingExpansion(leftTokenList, rightTokenList, 3)) {
            score = Math.max(score, 0.90);
        }

        return Math.min(1.0, score);
    }

    private List<String> canonicalTokens(String value) {
        String words = canonicalWords(value);
        if (words.isEmpty()) return List.of();
        List<String> tokens = new ArrayList<>(List.of(words.split("\\s+")));
        if (tokens.size() >= 2
                && tokens.get(tokens.size() - 2).equals("standard")
                && tokens.get(tokens.size() - 1).equals("edition")) {
            tokens = new ArrayList<>(tokens.subList(0, tokens.size() - 2));
        }
        return tokens;
    }

    private String canonicalCompact(String value) {
        return String.join("", canonicalTokens(value));
    }

    private String canonicalWords(String value) {
        if (value == null) return "";
        String normalized = normalizeUnicode(value)
                .replaceAll("['’‘ʼ`]+", "")
                .toLowerCase(Locale.ROOT);
        normalized = expandSmallNumericRanges(normalized)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) return "";

        List<String> raw = new ArrayList<>(List.of(normalized.split("\\s+")));
        boolean hasOtherRoman = raw.stream().anyMatch(token ->
                token.length() >= 2 && romanToInt(token) != null);
        List<String> canonical = new ArrayList<>();
        for (String token : raw) {
            if (token.equals("zero")) {
                canonical.add("0");
                continue;
            }
            Integer roman = romanToInt(token);
            if (roman != null && (token.length() >= 2 || hasOtherRoman)) {
                canonical.add(String.valueOf(roman));
                continue;
            }
            canonical.add(token);
        }
        return String.join(" ", canonical);
    }

    private String normalizeUnicode(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
    }

    private String expandSmallNumericRanges(String value) {
        Matcher matcher = NUMERIC_RANGE_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Integer start = numericTokenValue(matcher.group(1));
            Integer end = numericTokenValue(matcher.group(2));
            if (start == null || end == null || start < 0 || end < start || end - start > 10 || end > 20) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            StringBuilder replacement = new StringBuilder();
            for (int current = start; current <= end; current++) {
                if (!replacement.isEmpty()) replacement.append(' ');
                replacement.append(current);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Integer numericTokenValue(String token) {
        if (token == null || token.isBlank()) return null;
        if (token.matches("\\d+")) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return romanToInt(token);
    }

    private Integer romanToInt(String token) {
        if (token == null || token.isBlank()) return null;
        String roman = token.toUpperCase(Locale.ROOT);
        if (!roman.matches("[IVXLCDM]+")) return null;
        int total = 0;
        int previous = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int current = switch (roman.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            total += current < previous ? -current : current;
            previous = Math.max(previous, current);
        }
        if (total <= 0 || total > 20 || !toRoman(total).equals(roman)) return null;
        return total;
    }

    private String toRoman(int value) {
        int remaining = value;
        StringBuilder result = new StringBuilder();
        int[] values = {10, 9, 5, 4, 1};
        String[] symbols = {"X", "IX", "V", "IV", "I"};
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                result.append(symbols[i]);
                remaining -= values[i];
            }
        }
        return result.toString();
    }

    private boolean isSafeLeadingExpansion(List<String> detected, List<String> candidate, int maxLeadingTokens) {
        if (detected.size() < 2 || candidate.size() <= detected.size()) return false;
        int extraLeadingTokens = candidate.size() - detected.size();
        if (extraLeadingTokens > maxLeadingTokens) return false;
        for (int i = 0; i < detected.size(); i++) {
            if (!detected.get(i).equals(candidate.get(i + extraLeadingTokens))) return false;
        }
        return true;
    }

    private String stripLeadingArticle(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.trim().split("\\s+", 2);
        if (parts.length == 2 && isArticle(parts[0])) return parts[1];
        return value.trim();
    }

    private boolean isArticle(String token) {
        return token.equals("the") || token.equals("a") || token.equals("an");
    }

    private double levenshteinSimilarity(String left, String right) {
        if (left.equals(right)) return 1.0;
        int max = Math.max(left.length(), right.length());
        if (max == 0) return 1.0;
        return 1.0 - ((double) levenshteinDistance(left, right) / max);
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredCandidate(
            EntityReviewDto.Candidate candidate,
            double sortScore,
            double identityScore) {
    }
}
