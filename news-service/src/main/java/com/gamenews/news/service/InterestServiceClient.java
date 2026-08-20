package com.gamenews.news.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class InterestServiceClient {

    private final WebClient webClient;

    public InterestServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${service.interest-service.url}") String interestServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(interestServiceUrl).build();
    }

    public void mergeGameReferences(Long sourceGameId, Long targetGameId) {
        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/interests/games/merge")
                            .queryParam("sourceGameId", sourceGameId)
                            .queryParam("targetGameId", targetGameId)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Interest Service 게임 병합 처리에 실패했습니다", e);
        }
    }

}
