package com.gamenews.news.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GameSimilarityService {

    private static final int MIN_FUZZY_IDENTITY_LENGTH = 5;
    private static final double EXACT_IDENTITY_SCORE = 0.93;
    private static final double FUZZY_IDENTITY_WEIGHT = 0.88;
    private static final double CONTAINS_IDENTITY_SCORE = 0.72;
    private static final double DUPLICATE_CANDIDATE_IDENTITY_THRESHOLD = 0.55;

    public SimilarityResult compare(
            List<String> leftIdentities,
            List<String> rightIdentities,
            String leftPublisher,
            String rightPublisher,
            String leftDeveloper,
            String rightDeveloper) {

        double bestIdentityScore = 0.0;
        boolean exactIdentity = false;
        String bestLeftIdentity = null;
        String bestRightIdentity = null;

        for (String left : safe(leftIdentities)) {
            for (String right : safe(rightIdentities)) {
                String normalizedLeft = normalize(left);
                String normalizedRight = normalize(right);
                if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
                    continue;
                }

                if (normalizedLeft.equals(normalizedRight)) {
                    exactIdentity = true;
                    bestIdentityScore = EXACT_IDENTITY_SCORE;
                    bestLeftIdentity = cleanIdentity(left);
                    bestRightIdentity = cleanIdentity(right);
                    break;
                }

                // 짧은 약어/별칭(NTE, GTA 등)은 우연한 부분 포함이나 한 글자 차이로
                // 높은 유사도가 생기기 쉬우므로 exact match 외 fuzzy 비교에서 제외한다.
                if (!isFuzzyComparable(normalizedLeft, normalizedRight)) {
                    continue;
                }

                double similarity = levenshteinSimilarity(normalizedLeft, normalizedRight)
                        * FUZZY_IDENTITY_WEIGHT;

                int shorterLength = Math.min(normalizedLeft.length(), normalizedRight.length());
                if (shorterLength >= MIN_FUZZY_IDENTITY_LENGTH
                        && (normalizedLeft.contains(normalizedRight)
                        || normalizedRight.contains(normalizedLeft))) {
                    similarity = Math.max(similarity, CONTAINS_IDENTITY_SCORE);
                }

                if (similarity > bestIdentityScore) {
                    bestIdentityScore = similarity;
                    bestLeftIdentity = cleanIdentity(left);
                    bestRightIdentity = cleanIdentity(right);
                }
            }
            if (exactIdentity) {
                break;
            }
        }

        List<String> reasons = new ArrayList<>();
        if (exactIdentity) {
            reasons.add("게임명/별칭 정확 일치" + identityPair(bestLeftIdentity, bestRightIdentity));
        } else if (bestIdentityScore >= 0.45) {
            long similarityPercent = Math.round(
                    Math.min(1.0, bestIdentityScore / FUZZY_IDENTITY_WEIGHT) * 100);
            reasons.add("이름/별칭 유사도 " + similarityPercent + "%"
                    + identityPair(bestLeftIdentity, bestRightIdentity));
        }

        double score = bestIdentityScore;
        if (sameText(leftPublisher, rightPublisher)) {
            score += 0.04;
            reasons.add("퍼블리셔 일치");
        }
        if (sameText(leftDeveloper, rightDeveloper)) {
            score += 0.04;
            reasons.add("개발사 일치");
        }

        return new SimilarityResult(
                Math.min(0.99, score),
                bestIdentityScore,
                bestIdentityScore >= DUPLICATE_CANDIDATE_IDENTITY_THRESHOLD,
                reasons);
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean isFuzzyComparable(String left, String right) {
        return left.length() >= MIN_FUZZY_IDENTITY_LENGTH
                && right.length() >= MIN_FUZZY_IDENTITY_LENGTH;
    }

    private String cleanIdentity(String value) {
        return value == null ? "" : value.trim();
    }

    private String identityPair(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return "";
        }
        return " (" + left + " ↔ " + right + ")";
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private double levenshteinSimilarity(String left, String right) {
        if (left.equals(right)) {
            return 1.0;
        }
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshteinDistance(left, right) / maxLength);
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }

    public record SimilarityResult(
            double score,
            double identityScore,
            boolean duplicateCandidate,
            List<String> reasons) {
    }
}
