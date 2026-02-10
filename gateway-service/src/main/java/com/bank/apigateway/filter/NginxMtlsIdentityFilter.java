package com.bank.apigateway.filter;

import com.bank.apigateway.model.Counterparty;
import com.bank.apigateway.model.Counterparty.AuthType;
import com.bank.apigateway.service.CounterpartyService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Извлекает identity контрагента из заголовков, которые Nginx передаёт после mTLS.
 * Nginx устанавливает X-SSL-Client-DN (Subject DN сертификата) при успешной верификации.
 * CN из DN используется как externalId для маппинга на Counterparty.
 */
@Component
public class NginxMtlsIdentityFilter implements GlobalFilter, Ordered {

    private static final String COUNTERPARTY_ATTR = "counterparty";
    private static final String COUNTERPARTY_ID_HEADER = "X-Counterparty-Id";
    private static final String AUTH_TYPE_HEADER = "X-Auth-Type";
    private static final String SSL_CLIENT_DN_HEADER = "X-SSL-Client-DN";
    private static final String SSL_CLIENT_VERIFY_HEADER = "X-SSL-Client-Verify";

    private final CounterpartyService counterpartyService;

    public NginxMtlsIdentityFilter(CounterpartyService counterpartyService) {
        this.counterpartyService = counterpartyService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String sslVerify = exchange.getRequest().getHeaders().getFirst(SSL_CLIENT_VERIFY_HEADER);
        if (!"SUCCESS".equals(sslVerify)) {
            return chain.filter(exchange); // mTLS не пройден — передаём в OAuth
        }

        String dn = exchange.getRequest().getHeaders().getFirst(SSL_CLIENT_DN_HEADER);
        if (dn == null || dn.isBlank()) {
            return chain.filter(exchange);
        }

        String cn = parseCnFromDn(dn);
        if (cn == null) {
            return chain.filter(exchange);
        }

        return counterpartyService.findByExternalId(cn, AuthType.MTLS)
                .filter(Counterparty::active)
                .flatMap(counterparty -> {
                    exchange.getAttributes().put(COUNTERPARTY_ATTR, counterparty);
                    var request = exchange.getRequest().mutate()
                            .header(COUNTERPARTY_ID_HEADER, counterparty.id())
                            .header(AUTH_TYPE_HEADER, counterparty.authType().name())
                            .build();
                    return chain.filter(exchange.mutate().request(request).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private String parseCnFromDn(String dn) {
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3).trim();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.NGINX_MTLS;
    }
}
