package com.gamenews.news.service;

import com.gamenews.news.dto.NewsArticleDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.enums.AnalysisStatus;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private final NewsArticleRepository newsArticleRepository;

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
                .collectedAt(LocalDateTime.now())
                .content(trimToNull(request.getContent()))
                .category(request.getCategory())
                .analysisStatus(AnalysisStatus.PENDING)
                .build();

        return NewsArticleDto.NewsArticleResponse.from(newsArticleRepository.save(article));
    }

    public List<NewsArticleDto.NewsArticleResponse> getAllNews() {
        return newsArticleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NewsArticleDto.NewsArticleResponse::from)
                .toList();
    }

    public NewsArticleDto.NewsArticleResponse getNews(Long id) {
        return NewsArticleDto.NewsArticleResponse.from(findNewsById(id));
    }

    private NewsArticle findNewsById(Long id) {
        return newsArticleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
