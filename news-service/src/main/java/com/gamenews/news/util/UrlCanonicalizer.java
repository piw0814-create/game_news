package com.gamenews.news.util;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlCanonicalizer {

    private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
            "fbclid",
            "gclid",
            "ref",
            "ref_src"
    );

    public String canonicalize(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }

        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return conservativeFallback(trimmed);
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = normalizePort(scheme, uri.getPort());
            String path = normalizePath(uri.getRawPath());
            String query = normalizeQuery(uri.getRawQuery());

            return buildCanonicalUrl(
                    scheme,
                    uri.getRawUserInfo(),
                    host,
                    port,
                    path,
                    query);
        } catch (Exception ignored) {
            return conservativeFallback(trimmed);
        }
    }


    private String buildCanonicalUrl(
            String scheme,
            String rawUserInfo,
            String host,
            int port,
            String path,
            String query) {
        StringBuilder result = new StringBuilder();
        result.append(scheme).append("://");
        if (rawUserInfo != null && !rawUserInfo.isBlank()) {
            result.append(rawUserInfo).append('@');
        }
        result.append(host);
        if (port >= 0) {
            result.append(':').append(port);
        }
        result.append(path);
        if (query != null && !query.isBlank()) {
            result.append('?').append(query);
        }
        return result.toString();
    }

    private int normalizePort(String scheme, int port) {
        if (("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath)) {
            return "";
        }

        String normalized = rawPath;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        List<QueryPart> retained = new ArrayList<>();
        for (String part : rawQuery.split("&", -1)) {
            if (part.isEmpty()) {
                continue;
            }

            int separator = part.indexOf('=');
            String rawKey = separator >= 0 ? part.substring(0, separator) : part;
            String rawValue = separator >= 0 ? part.substring(separator + 1) : null;
            String decodedKey = decodeQueryComponent(rawKey).toLowerCase(Locale.ROOT);

            if (isTrackingKey(decodedKey)) {
                continue;
            }
            retained.add(new QueryPart(rawKey, rawValue));
        }

        if (retained.isEmpty()) {
            return null;
        }

        retained.sort(Comparator
                .comparing(QueryPart::key)
                .thenComparing(part -> part.value() == null ? "" : part.value()));

        return retained.stream()
                .map(QueryPart::render)
                .reduce((left, right) -> left + "&" + right)
                .orElse(null);
    }

    private boolean isTrackingKey(String key) {
        return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key);
    }

    private String decodeQueryComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private String conservativeFallback(String value) {
        int fragmentIndex = value.indexOf('#');
        String withoutFragment = fragmentIndex >= 0 ? value.substring(0, fragmentIndex) : value;
        if (withoutFragment.length() > 1 && withoutFragment.endsWith("/")) {
            return withoutFragment.substring(0, withoutFragment.length() - 1);
        }
        return withoutFragment;
    }

    private record QueryPart(String key, String value) {
        String render() {
            return value == null ? key : key + "=" + value;
        }
    }
}
