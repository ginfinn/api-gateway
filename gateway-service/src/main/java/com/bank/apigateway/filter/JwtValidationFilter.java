package com.bank.apigateway.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * mTLS + JWT: валидация Bearer JWT для авторизации (scope, claims).
 * Для /api/** требуется валидный JWT. Identity — из mTLS (NginxMtlsIdentityFilter).
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final Optional<JwtDecoder> jwtDecoder;

    public JwtValidationFilter(@Autowired(required = false) JwtDecoder jwtDecoder) {
        this.jwtDecoder = Optional.ofNullable(jwtDecoder);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/")) {
            return chain.filter(exchange);
        }

        if (jwtDecoder.isEmpty()) {
            return unauthorized(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = auth.substring(BEARER_PREFIX.length()).trim();
        JwtDecoder decoder = jwtDecoder.get();
        return Mono.fromCallable(() -> decoder.decode(token))
                .flatMap(jwt -> {
                    exchange.getAttributes().put("jwt", jwt);
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> unauthorized(exchange));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.JWT;
    }
}
