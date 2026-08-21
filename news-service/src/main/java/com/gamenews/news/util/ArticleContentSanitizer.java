package com.gamenews.news.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class ArticleContentSanitizer {

    private static final String REMOVED_ELEMENTS =
            "script,style,noscript,iframe,svg,form,template";

    /**
     * 외부 기사 본문을 AI 분석에 적합한 plain text로 정규화한다.
     * 명령처럼 보이는 자연어 자체는 삭제하지 않고, 실행 가능한/비가시 HTML 요소와
     * markup/entity/control-character 노이즈만 제거한다.
     */
    public String sanitize(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return null;
        }

        Document document = Jsoup.parseBodyFragment(rawContent);
        document.select(REMOVED_ELEMENTS).remove();

        String text = document.body().text();
        text = removeControlCharacters(text)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return text.isEmpty() ? null : text;
    }

    private String removeControlCharacters(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isISOControl(ch) || Character.isWhitespace(ch)) {
                cleaned.append(ch);
            }
        }
        return cleaned.toString();
    }
}
