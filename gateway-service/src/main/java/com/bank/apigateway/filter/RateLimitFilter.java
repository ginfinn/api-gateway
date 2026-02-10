package com.bank.apigateway.filter;

import com.bank.apigateway.model.Counterparty;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting по контрагенту (Bucket4j).
 * В продакшене — Redis для распределённого счётчика.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String COUNTERPARTY_ATTR = "counterparty";
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Counterparty counterparty = exchange.getAttribute(COUNTERPARTY_ATTR);
        if (counterparty == null) {
            return chain.filter(exchange);
        }

        Bucket bucket = buckets.computeIfAbsent(counterparty.id(), id -> createBucket(counterparty.rateLimitPerMinute()));
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.RATE_LIMIT;
    }
}
