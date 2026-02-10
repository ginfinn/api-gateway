package com.bank.apigateway.service;

import com.bank.apigateway.model.Counterparty;
import com.bank.apigateway.model.Counterparty.AuthType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Сервис загрузки данных контрагента.
 * Заглушка: в продакшене — обращение к БД или внутреннему API.
 */
@Service
public class CounterpartyService {

    // In-memory stub для разработки
    public Mono<Counterparty> findByExternalId(String externalId, AuthType authType) {
        return Mono.justOrEmpty(loadStub(externalId));
    }

    public Mono<Counterparty> findById(String id) {
        return Mono.justOrEmpty(loadStub(id));
    }

    private Counterparty loadStub(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return new Counterparty(
                "stub-" + externalId.hashCode(),
                externalId,
                AuthType.MTLS_OAUTH,
                Set.of("operations:read", "documents:read", "documents:write"),
                100,
                true
        );
    }
}
