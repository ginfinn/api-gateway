package com.bank.apigateway.model;

import java.util.Set;

/**
 * Контекст контрагента для текущего запроса.
 */
public record Counterparty(
        String id,
        String externalId,
        AuthType authType,
        Set<String> scopes,
        int rateLimitPerMinute,
        boolean active
) {
    public enum AuthType {
        MTLS,
        API_KEY,
        MTLS_OAUTH
    }

    public boolean hasScope(String scope) {
        return scopes != null && (scopes.contains(scope) || scopes.contains("*"));
    }
}
