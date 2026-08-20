package com.gamenews.news.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class InsightServiceClient {

    private final WebClient webClient;

    public InsightServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${service.insight-service.url:http://localhost:8085}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public void reanalyzeTopic(Long topicId) {
        webClient.post()
                .uri("/api/internal/insights/topics/{topicId}/reanalyze", topicId)
                .retrieve()
                .toBodilessEntity()
                .block();
        log.info("[EntityReview] Topic reanalysis requested - topicId={}", topicId);
    }
}
