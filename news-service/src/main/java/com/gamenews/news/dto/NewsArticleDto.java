package com.gamenews.news.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.enums.ArticleEntityType;
import com.gamenews.news.enums.NewsCategory;
import com.gamenews.news.enums.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class NewsArticleDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "기사 제목은 필수입니다")
        @Size(max = 500, message = "기사 제목은 500자 이하여야 합니다")
        private String title;

        @NotBlank(message = "기사 URL은 필수입니다")
        @Size(max = 768, message = "기사 URL은 768자 이하여야 합니다")
        private String url;

        @NotBlank(message = "출처 이름은 필수입니다")
        @Size(max = 255, message = "출처 이름은 255자 이하여야 합니다")
        private String sourceName;

        @NotNull(message = "출처 유형은 필수입니다")
        private SourceType sourceType;

        private LocalDateTime publishedAt;

        private String content;

        private NewsCategory category;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalysisStatusUpdateRequest {

        @NotNull(message = "분석 상태는 필수입니다")
        private AnalysisStatus status;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalysisUpdateRequest {

        @NotBlank(message = "기사 요약은 필수입니다")
        private String summary;

        @NotNull(message = "기사 카테고리는 필수입니다")
        private NewsCategory category;

        @NotNull(message = "키워드 목록은 필수입니다")
        @Size(max = 10, message = "키워드는 최대 10개까지 저장할 수 있습니다")
        private List<@Valid @NotBlank(message = "빈 키워드는 저장할 수 없습니다") String> keywords;

        @NotNull(message = "게임 뉴스 관련성 판단은 필수입니다")
        private Boolean gameNewsRelevant;

        @NotNull(message = "기사 엔티티 유형은 필수입니다")
        private ArticleEntityType entityType;
    }

    /**
     * Article Analyzer 결과를 파이프라인 중간 체크포인트로 저장한다.
     * summary/category 등 조회용 컬럼과 전체 정규화 결과(JSON)를 함께 보관한다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalysisCheckpointRequest {

        @NotBlank(message = "기사 요약은 필수입니다")
        private String summary;

        @NotNull(message = "기사 카테고리는 필수입니다")
        private NewsCategory category;

        @NotNull(message = "키워드 목록은 필수입니다")
        @Size(max = 10, message = "키워드는 최대 10개까지 저장할 수 있습니다")
        private List<@Valid @NotBlank(message = "빈 키워드는 저장할 수 없습니다") String> keywords;

        @NotNull(message = "게임 뉴스 관련성 판단은 필수입니다")
        private Boolean gameNewsRelevant;

        @NotNull(message = "기사 엔티티 유형은 필수입니다")
        private ArticleEntityType entityType;

        @Size(max = 500, message = "초기 Topic 제목은 500자 이하여야 합니다")
        private String initialTopicTitle;

        @Min(value = 0, message = "의미 중요도는 0 이상이어야 합니다")
        @Max(value = 50, message = "의미 중요도는 50 이하여야 합니다")
        private Integer semanticImportanceScore;

        private String initialWhyImportant;

        @NotBlank(message = "분석 체크포인트 payload는 필수입니다")
        private String analysisPayload;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewsArticleResponse {
        private Long id;
        private String title;
        private String url;
        private String canonicalUrl;
        private String sourceName;
        private SourceType sourceType;
        private OffsetDateTime publishedAt;
        private OffsetDateTime collectedAt;
        private String content;
        private String summary;
        private List<String> keywords;
        private NewsCategory category;
        private Boolean gameNewsRelevant;
        private ArticleEntityType entityType;
        private String initialTopicTitle;
        private Integer semanticImportanceScore;
        private String initialWhyImportant;
        private AnalysisStatus analysisStatus;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static NewsArticleResponse from(NewsArticle article) {
            return NewsArticleResponse.builder()
                    .id(article.getId())
                    .title(article.getTitle())
                    .url(article.getUrl())
                    .canonicalUrl(article.getCanonicalUrl())
                    .sourceName(article.getSourceName())
                    .sourceType(article.getSourceType())
                    .publishedAt(toUtc(article.getPublishedAt()))
                    .collectedAt(toUtc(article.getCollectedAt()))
                    .content(article.getContent())
                    .summary(article.getSummary())
                    .keywords(parseKeywords(article.getKeywords()))
                    .category(article.getCategory())
                    .gameNewsRelevant(article.getGameNewsRelevant())
                    .entityType(article.getEntityType())
                    .initialTopicTitle(article.getInitialTopicTitle())
                    .semanticImportanceScore(article.getSemanticImportanceScore())
                    .initialWhyImportant(article.getInitialWhyImportant())
                    .analysisStatus(article.getAnalysisStatus())
                    .createdAt(toUtc(article.getCreatedAt()))
                    .updatedAt(toUtc(article.getUpdatedAt()))
                    .build();
        }

        private static List<String> parseKeywords(String keywordsJson) {
            if (keywordsJson == null || keywordsJson.isBlank()) {
                return List.of();
            }

            try {
                return OBJECT_MAPPER.readValue(keywordsJson, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                return List.of();
            }
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
