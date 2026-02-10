package com.bank.apigateway.filter;

import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.core.Ordered;

/**
 * Порядок выполнения фильтров (от меньшего к большему).
 */
public final class GatewayFilterOrder {

    public static final int NGINX_MTLS = Ordered.HIGHEST_PRECEDENCE + 100;  // Identity из Nginx (mTLS)
    public static final int JWT = NGINX_MTLS + 10;                          // OAuth2 JWT
    public static final int COUNTERPARTY_CONTEXT = JWT + 10;
    public static final int RATE_LIMIT = COUNTERPARTY_CONTEXT + 10;
    public static final int AUDIT = RATE_LIMIT + 10;
    public static final int ROUTE = Ordered.LOWEST_PRECEDENCE - 100;

    private GatewayFilterOrder() {}
}
