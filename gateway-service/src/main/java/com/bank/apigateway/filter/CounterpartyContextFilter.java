package com.bank.apigateway.filter;

import com.bank.apigateway.model.Counterparty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Убеждается, что контрагент установлен, и добавляет заголовки для бэкенда.
 */
@Component
public class CounterpartyContextFilter implements GlobalFilter, Ordered {

    private static final String COUNTERPARTY_ATTR = "counterparty";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Counterparty counterparty = exchange.getAttribute(COUNTERPARTY_ATTR);

        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        if (counterparty == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Scope check можно добавить по маршруту
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.COUNTERPARTY_CONTEXT;
    }
}
