package com.gamenews.news.service;

import com.gamenews.news.dto.ArticleFranchiseDto;
import com.gamenews.news.entity.ArticleFranchise;
import com.gamenews.news.entity.Franchise;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.ArticleFranchiseRepository;
import com.gamenews.news.repository.FranchiseRepository;
import com.gamenews.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleFranchiseService {

    private final ArticleFranchiseRepository articleFranchiseRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final FranchiseRepository franchiseRepository;

    @Transactional
    public ArticleFranchiseDto.ArticleFranchiseResponse linkFranchise(
            Long articleId,
            ArticleFranchiseDto.CreateRequest request) {
        NewsArticle article = findArticle(articleId);
        Franchise franchise = findFranchise(request.getFranchiseId());

        if (articleFranchiseRepository.existsByArticle_IdAndFranchise_Id(
                articleId, request.getFranchiseId())) {
            throw new IllegalArgumentException("이미 기사에 연결된 프랜차이즈입니다: " + request.getFranchiseId());
        }

        ArticleFranchise relation = ArticleFranchise.builder()
                .article(article)
                .franchise(franchise)
                .primary(request.isPrimary())
                .confidenceScore(request.getConfidenceScore())
                .relevanceReason(normalizeReason(request.getRelevanceReason()))
                .build();

        return ArticleFranchiseDto.ArticleFranchiseResponse.from(
                articleFranchiseRepository.save(relation));
    }

    public List<ArticleFranchiseDto.ArticleFranchiseResponse> getFranchisesByArticle(Long articleId) {
        findArticle(articleId);
        return articleFranchiseRepository
                .findAllByArticle_IdOrderByPrimaryDescCreatedAtAsc(articleId).stream()
                .map(ArticleFranchiseDto.ArticleFranchiseResponse::from)
                .toList();
    }

    private NewsArticle findArticle(Long id) {
        return newsArticleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기사를 찾을 수 없습니다: " + id));
    }

    private Franchise findFranchise(Long id) {
        return franchiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프랜차이즈를 찾을 수 없습니다: " + id));
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
