package com.gamenews.news.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamenews.news.dto.NewsArticleDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.kafka.NewsCreatedEvent;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Transactional
    public NewsArticleDto.NewsArticleResponse createNews(NewsArticleDto.CreateRequest request) {
        String normalizedTitle = request.getTitle().trim();
        String normalizedUrl = request.getUrl().trim();
        String normalizedSourceName = request.getSourceName().trim();

        if (newsArticleRepository.existsByUrl(normalizedUrl)) {
            throw new IllegalArgumentException("이미 등록된 기사입니다: " + normalizedUrl);
        }

        NewsArticle article = NewsArticle.builder()
                .title(normalizedTitle)
                .url(normalizedUrl)
                .sourceName(normalizedSourceName)
                .sourceType(request.getSourceType())
                .publishedAt(request.getPublishedAt())
                .collectedAt(LocalDateTime.now(ZoneOffset.UTC))
                .content(trimToNull(request.getContent()))
                .category(request.getCategory())
                .analysisStatus(AnalysisStatus.PENDING)
                .build();

        NewsArticle savedArticle = newsArticleRepository.save(article);
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

    @Transactional
    public NewsArticleDto.NewsArticleResponse updateAnalysisStatus(
            Long id,
            NewsArticleDto.AnalysisStatusUpdateRequest request) {
        NewsArticle article = findNewsById(id);
        article.updateAnalysisStatus(request.getStatus());
        return NewsArticleDto.NewsArticleResponse.from(article);
    }

    @Transactional
    public NewsArticleDto.NewsArticleResponse updateAnalysis(
            Long id,
            NewsArticleDto.AnalysisUpdateRequest request) {
        NewsArticle article = findNewsById(id);

        String summary = request.getSummary().trim();
        List<String> normalizedKeywords = normalizeKeywords(request.getKeywords());
        String keywordsJson = toJson(normalizedKeywords);

        article.completeAnalysis(summary, request.getCategory(), keywordsJson);
        return NewsArticleDto.NewsArticleResponse.from(article);
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
