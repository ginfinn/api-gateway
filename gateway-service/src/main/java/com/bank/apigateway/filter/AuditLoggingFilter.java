package com.bank.apigateway.filter;

import com.bank.apigateway.model.Counterparty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Аудит запросов: counterparty, метод, путь, время.
 * В продакшене — отправка в audit-service или Kafka.
 */
@Component
public class AuditLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final String COUNTERPARTY_ATTR = "counterparty";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        Counterparty counterparty = exchange.getAttribute(COUNTERPARTY_ATTR);
        ServerHttpRequest request = exchange.getRequest();

        return chain.filter(exchange)
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    String cpId = counterparty != null ? counterparty.id() : "anonymous";
                    auditLog.info("AUDIT {} {} {} {} {}ms {}",
                            cpId,
                            request.getMethod(),
                            request.getPath().value(),
                            status,
                            duration,
                            request.getRemoteAddress());
                });
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.AUDIT;
    }
}
