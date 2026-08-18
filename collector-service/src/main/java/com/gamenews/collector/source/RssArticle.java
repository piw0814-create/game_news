package com.gamenews.collector.source;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RssArticle {
    private String title;
    private String url;
    private LocalDateTime publishedAt;
    private String content;
}
