package com.gamenews.collector.source;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GenericRssClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151 Safari/537.36";
    private static final String ACCEPT = "application/rss+xml, application/xml, text/xml, */*";

    private final WebClient.Builder webClientBuilder;
    private final RssSourceConfig sourceConfig;

    public List<RssArticle> fetchLatest(RssSourceConfig.Source source, int limit) {
        validateSource(source);

        try {
            byte[] rssBytes = webClientBuilder.build()
                    .get()
                    .uri(source.getRssUrl())
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .header(HttpHeaders.ACCEPT, ACCEPT)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(sourceConfig.getHttp().getRssTimeoutSeconds()))
                    .block();

            if (rssBytes == null || rssBytes.length == 0) {
                throw new IllegalStateException(source.getName() + " RSS 응답이 비어 있습니다");
            }

            try (XmlReader reader = new XmlReader(new ByteArrayInputStream(rssBytes))) {
                SyndFeed feed = new SyndFeedInput().build(reader);

                return feed.getEntries().stream()
                        .limit(limit)
                        .map(this::toArticle)
                        .toList();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    source.getName() + " RSS 수집에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private RssArticle toArticle(SyndEntry entry) {
        String content = extractContent(entry);
        Date publishedDate = entry.getPublishedDate() != null
                ? entry.getPublishedDate()
                : entry.getUpdatedDate();

        LocalDateTime publishedAt = publishedDate == null
                ? null
                : LocalDateTime.ofInstant(publishedDate.toInstant(), ZoneOffset.UTC);

        return RssArticle.builder()
                .title(entry.getTitle())
                .url(entry.getLink())
                .publishedAt(publishedAt)
                .content(content)
                .build();
    }

    private String extractContent(SyndEntry entry) {
        if (entry.getContents() != null) {
            for (SyndContent content : entry.getContents()) {
                if (content != null && content.getValue() != null && !content.getValue().isBlank()) {
                    return content.getValue();
                }
            }
        }

        return entry.getDescription() == null
                ? null
                : entry.getDescription().getValue();
    }

    private void validateSource(RssSourceConfig.Source source) {
        if (source.getName() == null || source.getName().isBlank()) {
            throw new IllegalArgumentException("RSS 출처 이름이 비어 있습니다");
        }
        if (source.getRssUrl() == null || source.getRssUrl().isBlank()) {
            throw new IllegalArgumentException(source.getName() + " RSS URL이 비어 있습니다");
        }
    }
}
