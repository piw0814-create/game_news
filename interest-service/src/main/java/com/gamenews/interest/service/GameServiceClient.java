package com.gamenews.interest.service;

import com.gamenews.interest.dto.InterestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${service.news-service.url}")
    private String newsServiceUrl;

    public InterestDto.GameSummary getGame(Long gameId) {
        try {
            Map<String, Object> responseBody = webClientBuilder.build()
                    .get()
                    .uri(newsServiceUrl + "/api/games/{id}", gameId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        if (response.statusCode().value() == 404) {
                            return response.createException();
                        }
                        return response.createException();
                    })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (responseBody == null) {
                throw new RuntimeException("News Service 응답 본문이 비어 있습니다");
            }

            Object data = responseBody.get("data");
            if (!(data instanceof Map<?, ?> dataMap)) {
                throw new RuntimeException("News Service Game 응답 형식이 올바르지 않습니다");
            }

            return InterestDto.GameSummary.builder()
                    .id(toLong(dataMap.get("id")))
                    .name(toStringValue(dataMap.get("name")))
                    .publisher(toStringValue(dataMap.get("publisher")))
                    .genre(toStringValue(dataMap.get("genre")))
                    .platform(toStringValue(dataMap.get("platform")))
                    .imageUrl(toStringValue(dataMap.get("imageUrl")))
                    .build();
        } catch (WebClientResponseException.NotFound | WebClientResponseException.BadRequest e) {
            log.warn("[GameServiceClient] 게임 없음 - gameId={}, status={}", gameId, e.getStatusCode());
            throw new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GameServiceClient] Game 조회 실패 - gameId={}, error={}", gameId, e.getMessage());
            throw new RuntimeException("News Service 연결 실패");
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
