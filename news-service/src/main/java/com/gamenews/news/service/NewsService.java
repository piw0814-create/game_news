package com.gamenews.news.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamenews.news.dto.NewsArticleDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.kafka.NewsCreatedEvent;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.util.ArticleContentSanitizer;
import com.gamenews.news.util.UrlCanonicalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private final NewsArticleRepository newsArticleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;
    private final ArticleContentSanitizer articleContentSanitizer;

    @Transactional
    public NewsArticleDto.NewsArticleResponse createNews(NewsArticleDto.CreateRequest request) {
        String normalizedTitle = request.getTitle().trim();
        String normalizedUrl = request.getUrl().trim();
        String normalizedSourceName = request.getSourceName().trim();
        String canonicalUrl = urlCanonicalizer.canonicalize(normalizedUrl);

        if (newsArticleRepository.existsByUrl(normalizedUrl)
                || newsArticleRepository.existsByCanonicalUrl(canonicalUrl)
                || existsLegacyCanonicalDuplicate(canonicalUrl)) {
            throw new IllegalArgumentException("이미 등록된 기사입니다: " + normalizedUrl);
        }

        NewsArticle article = NewsArticle.builder()
                .title(normalizedTitle)
                .url(normalizedUrl)
                .canonicalUrl(canonicalUrl)
                .sourceName(normalizedSourceName)
                .sourceType(request.getSourceType())
                .publishedAt(request.getPublishedAt())
                .collectedAt(LocalDateTime.now(ZoneOffset.UTC))
                .content(articleContentSanitizer.sanitize(request.getContent()))
                .category(request.getCategory())
                .analysisStatus(AnalysisStatus.PENDING)
                .build();

        NewsArticle savedArticle;
        try {
            savedArticle = newsArticleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 등록된 기사입니다: " + normalizedUrl, e);
        }
        eventPublisher.publishEvent(new NewsCreatedEvent(savedArticle.getId()));

        return NewsArticleDto.NewsArticleResponse.from(savedArticle);
    }

    public List<NewsArticleDto.NewsArticleResponse> getAllNews() {
        return newsArticleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NewsArticleDto.NewsArticleResponse::from)
                .toList();
    }

    public NewsArticleDto.NewsArticleResponse getNews(Long id) {
        return NewsArticleDto.NewsArticleResponse.from(findNewsById(id));
    }

    public OffsetDateTime getLatestPublishedAtBySource(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("출처 이름이 비어 있습니다");
        }

        String normalizedSourceName = sourceName.trim();
        return newsArticleRepository
                .findTopBySourceNameAndPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(normalizedSourceName)
                .map(article -> article.getPublishedAt().atOffset(ZoneOffset.UTC))
                .orElse(null);
    }

    public List<NewsArticleDto.NewsArticleResponse> getRecoveryCandidates(
            int limit,
            int processingStaleMinutes,
            int pendingStaleMinutes,
            List<Long> excludeIds) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        int normalizedProcessingStaleMinutes = Math.max(1, Math.min(processingStaleMinutes, 1440));
        boolean includeFreshPending = pendingStaleMinutes <= 0;
        int normalizedPendingStaleMinutes = Math.max(1, Math.min(pendingStaleMinutes, 1440));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime processingStaleBefore = now.minusMinutes(normalizedProcessingStaleMinutes);
        LocalDateTime pendingStaleBefore = now.minusMinutes(normalizedPendingStaleMinutes);

        List<Long> normalizedExcludeIds = excludeIds == null
                ? List.of(-1L)
                : excludeIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .limit(500)
                        .toList();
        if (normalizedExcludeIds.isEmpty()) {
            normalizedExcludeIds = List.of(-1L);
        }

        List<NewsArticle> candidates = includeFreshPending
                ? newsArticleRepository.findStartupRecoveryCandidates(
                        AnalysisStatus.PENDING,
                        AnalysisStatus.FAILED,
                        AnalysisStatus.PROCESSING,
                        AnalysisStatus.ANALYZED,
                        AnalysisStatus.TOPIC_PENDING,
                        processingStaleBefore,
                        normalizedExcludeIds,
                        PageRequest.of(0, normalizedLimit))
                : newsArticleRepository.findPeriodicRecoveryCandidates(
                        AnalysisStatus.PENDING,
                        AnalysisStatus.FAILED,
                        AnalysisStatus.PROCESSING,
                        AnalysisStatus.ANALYZED,
                        AnalysisStatus.TOPIC_PENDING,
                        pendingStaleBefore,
                        processingStaleBefore,
                        normalizedExcludeIds,
                        PageRequest.of(0, normalizedLimit));

        return candidates.stream()
                .map(NewsArticleDto.NewsArticleResponse::from)
                .toList();
    }

    @Transactional
    public NewsArticleDto.NewsArticleResponse updateAnalysisStatus(
            Long id,
            NewsArticleDto.AnalysisStatusUpdateRequest request) {
        NewsArticle article = findNewsById(id);
        article.updateAnalysisStatus(request.getStatus());
        return NewsArticleDto.NewsArticleResponse.from(article);
    }

    @Transactional
    public NewsArticleDto.NewsArticleResponse saveAnalysisCheckpoint(
            Long id,
            NewsArticleDto.AnalysisCheckpointRequest request) {
        NewsArticle article = findNewsById(id);

        String summary = request.getSummary().trim();
        List<String> normalizedKeywords = normalizeKeywords(request.getKeywords());
        String keywordsJson = toJson(normalizedKeywords);

        article.saveAnalysisCheckpoint(
                summary,
                request.getCategory(),
                keywordsJson,
                request.getGameNewsRelevant(),
                request.getEntityType(),
                trimToNull(request.getInitialTopicTitle()),
                request.getSemanticImportanceScore(),
                trimToNull(request.getInitialWhyImportant()),
                request.getAnalysisPayload().trim());
        return NewsArticleDto.NewsArticleResponse.from(article);
    }

    public String getAnalysisCheckpoint(Long id) {
        NewsArticle article = findNewsById(id);
        String checkpoint = trimToNull(article.getAnalysisCheckpoint());
        if (checkpoint == null) {
            throw new IllegalStateException("저장된 기사 분석 체크포인트가 없습니다: " + id);
        }
        return checkpoint;
    }

    @Transactional
    public NewsArticleDto.NewsArticleResponse updateAnalysis(
            Long id,
            NewsArticleDto.AnalysisUpdateRequest request) {
        NewsArticle article = findNewsById(id);

        String summary = request.getSummary().trim();
        List<String> normalizedKeywords = normalizeKeywords(request.getKeywords());
        String keywordsJson = toJson(normalizedKeywords);

        article.completeAnalysis(
                summary,
                request.getCategory(),
                keywordsJson,
                request.getGameNewsRelevant(),
                request.getEntityType());
        return NewsArticleDto.NewsArticleResponse.from(article);
    }

    private boolean existsLegacyCanonicalDuplicate(String canonicalUrl) {
        return newsArticleRepository.findAllByCanonicalUrlIsNullOrderByIdAsc().stream()
                .map(NewsArticle::getUrl)
                .map(urlCanonicalizer::canonicalize)
                .anyMatch(canonicalUrl::equals);
    }

    private NewsArticle findNewsById(Long id) {
        return newsArticleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + id));
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }

        LinkedHashSet<String> uniqueKeywords = new LinkedHashSet<>();
        for (String keyword : keywords) {
            String normalized = trimToNull(keyword);
            if (normalized != null) {
                uniqueKeywords.add(normalized);
            }
        }
        return uniqueKeywords.stream().limit(10).toList();
    }

    private String toJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("키워드 JSON 변환에 실패했습니다", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
