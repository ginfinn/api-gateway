package com.bank.apigateway.filter;

import com.bank.apigateway.model.Counterparty;
import com.bank.apigateway.model.Counterparty.AuthType;
import com.bank.apigateway.service.CounterpartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Валидация JWT Bearer токена (OAuth2).
 * Использует JwtDecoder из spring-security-oauth2-resource-server.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String COUNTERPARTY_ATTR = "counterparty";
    private static final String COUNTERPARTY_ID_HEADER = "X-Counterparty-Id";
    private static final String AUTH_TYPE_HEADER = "X-Auth-Type";
    private static final String BEARER_PREFIX = "Bearer ";

    private final CounterpartyService counterpartyService;
    private final Optional<JwtDecoder> jwtDecoder;

    public JwtValidationFilter(CounterpartyService counterpartyService,
                               @Autowired(required = false) JwtDecoder jwtDecoder) {
        this.counterpartyService = counterpartyService;
        this.jwtDecoder = Optional.ofNullable(jwtDecoder);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getAttribute(COUNTERPARTY_ATTR) != null) {
            return chain.filter(exchange); // уже аутентифицирован (mTLS через Nginx)
        }

        if (jwtDecoder.isEmpty()) {
            return requireAuthOrContinue(exchange, chain);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            return requireAuthOrContinue(exchange, chain);
        }

        String token = auth.substring(BEARER_PREFIX.length()).trim();
        JwtDecoder decoder = jwtDecoder.get();
        return Mono.fromCallable(() -> decoder.decode(token))
                .flatMap(jwt -> resolveCounterparty(jwt)
                        .filter(Counterparty::active)
                        .flatMap(counterparty -> {
                            exchange.getAttributes().put(COUNTERPARTY_ATTR, counterparty);
                            var request = exchange.getRequest().mutate()
                                    .header(COUNTERPARTY_ID_HEADER, counterparty.id())
                                    .header(AUTH_TYPE_HEADER, counterparty.authType().name())
                                    .build();
                            return chain.filter(exchange.mutate().request(request).build());
                        }))
                .switchIfEmpty(requireAuthOrContinue(exchange, chain))
                .onErrorResume(e -> unauthorized(exchange));
    }

    private Mono<Counterparty> resolveCounterparty(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId == null) {
            clientId = jwt.getSubject();
        }
        if (clientId == null || clientId.isBlank()) {
            return Mono.empty();
        }
        return counterpartyService.findByExternalId(clientId, AuthType.MTLS_OAUTH);
    }

    private Mono<Void> requireAuthOrContinue(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/")) {
            return unauthorized(exchange);
        }
        return chain.filter(exchange);
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
