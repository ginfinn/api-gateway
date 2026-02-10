# Nginx (mTLS) + Spring Boot Gateway (OAuth, Rate Limit)

Архитектура: **mTLS в Nginx** → **OAuth и rate limiting в Spring Boot**.

```
┌─────────────────────┐     JWT      ┌─────────────────────────────┐
│ Authorization       │ ◄─────────── │ Контрагенты                 │
│ Server              │   token      │ (client credentials)         │
│ (Keycloak и т.д.)   │              └──────────────┬──────────────┘
└─────────────────────┘                             │
                                                    │ Запрос (mTLS или Bearer)
                                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  NGINX (mTLS)                                                                │
│  • ssl_verify_client on                                                      │
│  • X-SSL-Client-DN, X-SSL-Client-Verify → Gateway                            │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │ proxy_pass
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  SPRING BOOT GATEWAY                                                         │
│  • NginxMtlsIdentityFilter  — CN из сертификата → counterparty               │
│  • JwtValidationFilter      — Bearer JWT (jwks от Auth Server)               │
│  • RateLimitFilter          — лимит по counterparty                          │
│  • AuditLoggingFilter       — аудит                                          │
│  • Маршрутизация            → operations-service, documents-service          │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │ X-Counterparty-Id
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  BACKEND-СЕРВИСЫ                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Требования

- Docker, Docker Compose
- OpenSSL (для генерации сертификатов)

## Быстрый старт

### 1. Сгенерировать сертификаты

**Windows:**
```powershell
.\scripts\generate-certs.ps1
```

**Linux/macOS:**
```bash
chmod +x scripts/generate-certs.sh
./scripts/generate-certs.sh
```

### 2. Запуск

```bash
docker compose up -d
```

- **https://localhost:8443** — Nginx (mTLS), проксирует в Gateway
- **http://localhost:8080** — Gateway (для отладки, без mTLS)

### 3. Тест с клиентским сертификатом

```bash
curl -k --cert nginx/certs/client-cp-a.crt --key nginx/certs/client-cp-a.key \
  https://localhost:8443/api/v1/operations
```

## Конфигурация

| Компонент | Файл | Назначение |
|-----------|------|------------|
| Nginx | nginx/nginx.conf | mTLS, proxy_pass → gateway:8080 |
| Gateway | ../gateway-service | OAuth2 JWT, rate limit, audit |
| Сертификаты | nginx/certs/ | CA, server, client certs |

## OAuth2

Для включения OAuth2 укажите в `gateway-service/application.yml` или переменных окружения:

```yaml
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs
```

Без этой настройки аутентификация возможна только через mTLS (клиентский сертификат).

## Тестовые контрагенты

| CN в сертификате | Описание |
|------------------|----------|
| cp-a-001 | Counterparty A |
| cp-b-002 | Counterparty B |

CounterpartyService (заглушка) маппит CN на контрагента.
