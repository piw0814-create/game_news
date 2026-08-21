package com.gamenews.collector.client;

import com.gamenews.collector.dto.CollectorDto;
import com.gamenews.collector.exception.NewsServiceUnavailableException;
import com.gamenews.collector.source.RssSourceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class NewsServiceClient {

    private static final String DUPLICATE_MESSAGE = "이미 등록된 기사입니다";

    private final WebClient.Builder webClientBuilder;
    private final RssSourceConfig sourceConfig;

    @Value("${collector.news-service.base-url}")
    private String newsServiceBaseUrl;

    public LocalDateTime getLatestPublishedAt(String sourceName) {
        Mono<CollectorDto.ApiResponse<String>> request = webClientBuilder.build()
                .get()
                .uri(
                        newsServiceBaseUrl
                                + "/api/internal/news/latest-published-at?sourceName={sourceName}",
                        sourceName)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(
                                new ParameterizedTypeReference<CollectorDto.ApiResponse<String>>() {});
                    }

                    if (response.statusCode().is5xxServerError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new NewsServiceUnavailableException(
                                        "News Service 최신 기사 시각 조회 실패 (HTTP "
                                                + response.statusCode().value() + "): " + body)));
                    }

                    return response.createException().flatMap(exception -> Mono.error(exception));
                });

        CollectorDto.ApiResponse<String> response = blockWithNewsTimeout(
                request,
                "최신 기사 시각 조회"
        );

        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("News Service 최신 기사 시각 조회 결과가 비어 있습니다");
        }

        String latestPublishedAt = response.getData();
        if (latestPublishedAt == null || latestPublishedAt.isBlank()) {
            return null;
        }

        return OffsetDateTime.parse(latestPublishedAt)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    public SaveStatus createNews(CollectorDto.NewsCreateRequest request) {
        Mono<SaveStatus> saveRequest = webClientBuilder.build()
                .post()
                .uri(newsServiceBaseUrl + "/api/news")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.just(SaveStatus.SAVED);
                    }

                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                if (response.statusCode().value() == 400
                                        && body.contains(DUPLICATE_MESSAGE)) {
                                    return Mono.just(SaveStatus.DUPLICATE);
                                }

                                if (response.statusCode().is5xxServerError()) {
                                    return Mono.error(new NewsServiceUnavailableException(
                                            "News Service 저장 실패 (HTTP "
                                                    + response.statusCode().value()
                                                    + "): "
                                                    + body));
                                }

                                return Mono.error(new IllegalStateException(
                                        "News Service 저장 실패 (HTTP "
                                                + response.statusCode().value()
                                                + "): "
                                                + body));
                            });
                });

        SaveStatus result = blockWithNewsTimeout(saveRequest, "기사 저장");

        if (result == null) {
            throw new NewsServiceUnavailableException("News Service 저장 결과가 비어 있습니다");
        }

        return result;
    }

    private <T> T blockWithNewsTimeout(Mono<T> request, String operation) {
        try {
            return request
                    .timeout(Duration.ofSeconds(sourceConfig.getHttp().getNewsTimeoutSeconds()))
                    .block();
        } catch (NewsServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            if (hasCause(e, TimeoutException.class)) {
                throw new NewsServiceUnavailableException(
                        "News Service " + operation + " timeout", e);
            }
            if (hasCause(e, WebClientRequestException.class)) {
                throw new NewsServiceUnavailableException(
                        "News Service " + operation + " 연결 실패", e);
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("News Service " + operation + " 처리 실패", e);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public enum SaveStatus {
        SAVED,
        DUPLICATE
    }
}
