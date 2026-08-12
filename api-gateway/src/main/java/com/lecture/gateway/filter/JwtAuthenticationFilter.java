package com.lecture.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log =
        LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        return ReactiveSecurityContextHolder.getContext()
            .flatMap(context -> {

                if (!(context.getAuthentication()
                        instanceof JwtAuthenticationToken authentication)) {
                    return chain.filter(exchange);
                }

                Jwt jwt = authentication.getToken();

                String subject = jwt.getSubject();
                String userId = extractUserId(jwt, subject);
                String email = extractEmail(jwt, subject);
                String role = extractRole(jwt);

                log.debug(
                    "JWT Filter - subject: {}, userId: {}, email: {}, role: {}",
                    subject, userId, email, role
                );

                ServerHttpRequest request = exchange.getRequest()
                    .mutate()
                    .header("X-User-Id", safe(userId))
                    .header("X-User-Email", safe(email))
                    .header("X-User-Role", safe(role))
                    .build();

                return chain.filter(
                    exchange.mutate().request(request).build()
                );
            })
            .switchIfEmpty(chain.filter(exchange));
    }

    private String extractUserId(Jwt jwt, String subject) {
        String value = jwt.getClaimAsString("user_id");

        if (hasText(value)) {
            return value;
        }

        value = jwt.getClaimAsString("id");

        if (hasText(value)) {
            return value;
        }

        return subject;
    }

    private String extractEmail(Jwt jwt, String subject) {
        String value = jwt.getClaimAsString("email");

        if (hasText(value)) {
            return value;
        }

        if (looksLikeEmail(subject)) {
            return subject;
        }

        return "";
    }

    private String extractRole(Jwt jwt) {
        String value = jwt.getClaimAsString("role");
        return hasText(value) ? value : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean looksLikeEmail(String value) {
        return hasText(value) && value.contains("@");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
