package com.gamenews.news.service;

import com.gamenews.news.dto.ArticleContentMaintenanceDto;
import com.gamenews.news.entity.NewsArticle;
import com.gamenews.news.repository.NewsArticleRepository;
import com.gamenews.news.util.ArticleContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentMaintenanceServiceTest {

    @Mock
    private NewsArticleRepository newsArticleRepository;

    private ArticleContentMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new ArticleContentMaintenanceService(
                newsArticleRepository,
                new ArticleContentSanitizer());
    }

    @Test
    void dryRunReportsChangesWithoutUpdatingArticles() {
        NewsArticle changed = article(1L, "<p>Hello &amp; world</p><script>bad()</script>");
        NewsArticle unchanged = article(2L, "Already clean text");
        when(newsArticleRepository.findAllByContentIsNotNullOrderByIdAsc())
                .thenReturn(List.of(changed, unchanged));

        ArticleContentMaintenanceDto.SanitizeResponse response = service.sanitize(true);

        assertThat(response.getCandidates()).isEqualTo(2);
        assertThat(response.getChanged()).isEqualTo(1);
        assertThat(response.getUnchanged()).isEqualTo(1);
        assertThat(response.getUpdated()).isZero();
        assertThat(response.getTotalCharsAfter()).isLessThan(response.getTotalCharsBefore());
        assertThat(changed.getContent()).contains("<script>");
        verify(newsArticleRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applyUpdatesOnlyChangedContentWithoutTouchingAnalysisState() {
        NewsArticle changed = article(1L, "<p>Hello &amp; world</p>");
        when(newsArticleRepository.findAllByContentIsNotNullOrderByIdAsc())
                .thenReturn(List.of(changed));

        ArticleContentMaintenanceDto.SanitizeResponse response = service.sanitize(false);

        assertThat(response.getChanged()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(1);
        assertThat(changed.getContent()).isEqualTo("Hello & world");
        verify(newsArticleRepository).saveAll(List.of(changed));
        verify(newsArticleRepository).flush();
    }

    private NewsArticle article(Long id, String content) {
        return NewsArticle.builder()
                .id(id)
                .title("test")
                .url("https://example.com/" + id)
                .sourceName("source")
                .content(content)
                .build();
    }
}
