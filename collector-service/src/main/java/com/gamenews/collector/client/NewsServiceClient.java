package com.gamenews.collector.client;

import com.gamenews.collector.dto.CollectorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class NewsServiceClient {

    private static final String DUPLICATE_MESSAGE = "이미 등록된 기사입니다";

    private final WebClient.Builder webClientBuilder;

    @Value("${collector.news-service.base-url}")
    private String newsServiceBaseUrl;

    public SaveStatus createNews(CollectorDto.NewsCreateRequest request) {
        SaveStatus result = webClientBuilder.build()
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
                                if (response.statusCode().value() == 400 && body.contains(DUPLICATE_MESSAGE)) {
                                    return Mono.just(SaveStatus.DUPLICATE);
                                }

                                return Mono.error(new IllegalStateException(
                                        "News Service 저장 실패 (HTTP "
                                                + response.statusCode().value()
                                                + "): "
                                                + body));
                            });
                })
                .block();

        if (result == null) {
            throw new IllegalStateException("News Service 저장 결과가 비어 있습니다");
        }

        return result;
    }

    public enum SaveStatus {
        SAVED,
        DUPLICATE
    }
}
