package com.gamenews.news.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlCanonicalizerTest {

    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

    @Test
    void removesTrackingParametersAndFragment() {
        assertThat(canonicalizer.canonicalize(
                "https://Example.COM/news/123/?utm_source=rss&utm_medium=feed#comments"))
                .isEqualTo("https://example.com/news/123");
    }

    @Test
    void removesKnownClickTrackingParameters() {
        assertThat(canonicalizer.canonicalize(
                "https://example.com/news/123?fbclid=abc&gclid=def&ref=home&ref_src=twsrc"))
                .isEqualTo("https://example.com/news/123");
    }

    @Test
    void keepsSemanticQueryParametersAndSortsThem() {
        assertThat(canonicalizer.canonicalize(
                "https://example.com/news?b=2&id=123&a=1"))
                .isEqualTo("https://example.com/news?a=1&b=2&id=123");
    }

    @Test
    void differentSemanticIdsRemainDifferent() {
        assertThat(canonicalizer.canonicalize("https://example.com/news?id=123"))
                .isNotEqualTo(canonicalizer.canonicalize("https://example.com/news?id=456"));
    }

    @Test
    void removesDefaultPorts() {
        assertThat(canonicalizer.canonicalize("https://EXAMPLE.com:443/news/"))
                .isEqualTo("https://example.com/news");
        assertThat(canonicalizer.canonicalize("http://EXAMPLE.com:80/news/"))
                .isEqualTo("http://example.com/news");
    }

    @Test
    void rootSlashAndEmptyPathAreEquivalent() {
        assertThat(canonicalizer.canonicalize("https://example.com/"))
                .isEqualTo(canonicalizer.canonicalize("https://example.com"));
    }

    @Test
    void invalidUriFallsBackWithoutFragment() {
        assertThat(canonicalizer.canonicalize("not a valid uri#fragment"))
                .isEqualTo("not a valid uri");
    }
}
