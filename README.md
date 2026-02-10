# API Gateway — банковский бэкенд

Унифицированный API Gateway для авторизованных запросов от контрагентов к бэкенду банковской организации.

## Архитектура

### Полная схема (с Authorization Server)

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              КОНТРАГЕНТЫ (внешние системы)                                   │
│                                                                                             │
│   Контрагенты (mTLS + JWT): client cert + Bearer token                                       │
└───────┬─────────────────────────────────────────────────────────────────────────────────────┘
        │
        │ 1. Client Credentials → JWT
        ▼
        ┌─────────────────────────────────────────────────────────────┐
        │              AUTHORIZATION SERVER                            │
        │  (Keycloak / Spring Authorization Server / Auth0)            │
        │                                                              │
        │  • POST /oauth/token  (client_id, client_secret)             │
        │  • Выдаёт JWT access token                                  │
        │  • JWK Set URI (для валидации подписи)                       │
        └──────────────────────────┬──────────────────────────────────┘
                                   │
        JWT ◄──────────────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────────────┐
        │                          │  Запрос (mTLS + Bearer JWT)      │
        │                          ▼                                  │
        │  ┌──────────────────────────────────────────────────────┐   │
        │  │  NGINX (TLS Termination, mTLS)                        │   │
        │  │  • Верификация клиентского сертификата                │   │
        │  │  • X-SSL-Client-DN, X-SSL-Client-Verify → Gateway     │   │
        │  │  • proxy_pass → Gateway                               │   │
        │  └──────────────────────────┬───────────────────────────┘   │
        │                             │                               │
        │                             ▼                               │
        │  ┌──────────────────────────────────────────────────────┐   │
        │  │  SPRING BOOT GATEWAY (Resource Server)                │   │
        │  │                                                       │   │
        │  │  • NginxMtlsIdentityFilter  — CN → counterparty       │   │
        │  │  • JwtValidationFilter      — Bearer JWT (jwks)       │   │
        │  │  • CounterpartyContextFilter — проверка scope         │   │
        │  │  • RateLimitFilter          — лимит по counterparty   │   │
        │  │  • AuditLoggingFilter       — аудит запросов          │   │
        │  │  • Маршрутизация → Backend                             │   │
        │  └──────────────────────────┬───────────────────────────┘   │
        │                             │                               │
        └─────────────────────────────┼───────────────────────────────┘
                                      │
                                      │  X-Counterparty-Id, заголовки
                                      ▼
        ┌─────────────────────────────────────────────────────────────┐
        │                    BACKEND-СЕРВИСЫ                          │
        │                                                             │
        │  operations-service (8081)    documents-service (8082)       │
        └─────────────────────────────────────────────────────────────┘
```

### Потоки данных

#### Поток 1: mTLS + JWT (полный цикл)

```
Контрагент       Auth Server        Nginx              Gateway             Backend
    │                 │                │                   │                   │
    │  POST /oauth/token               │                   │                   │
    │  client_id, client_secret        │                   │                   │
    │ ───────────────►│                │                   │                   │
    │  200 {access_token}              │                   │                   │
    │ ◄────────────────                │                   │                   │
    │                 │                │                   │                   │
    │  GET /api/v1/ops + client cert + Authorization: Bearer <jwt>             │
    │ ────────────────────────────────►│                   │                   │
    │                  │  mTLS verify  │                   │                   │
    │                  │  + proxy_pass │                   │                   │
    │                  │  X-SSL-Client-DN, X-SSL-Client-Verify                 │
    │                  │ ─────────────►│                   │                   │
    │                  │               │  CN → counterparty │                   │
    │                  │               │  JWT validate      │                   │
    │                  │               │  rate limit, audit │                   │
    │                  │               │  + X-Counterparty-Id                   │
    │                  │               │ ─────────────────►│                   │
    │                  │               │                   │ 200 OK            │
    │  200 OK          │ ◄─────────────│ ◄─────────────────│                   │
    │ ◄────────────────│               │                   │                   │
```

#### Поток 2: Цепочка фильтров Gateway (один запрос)

```
   Request → NginxMtls → JwtValid → Counterparty → RateLimit → Audit → Backend
                │           │            │             │          │
                │           │            │             │          │
   Headers:     │           │            │             │          │
   X-SSL-* ────►│ CN → cp   │            │             │          │
   Bearer ────────────────►│ JWT valid  │             │          │
                │           │            │             │          │
   counterparty ◄───────────┴────────────┘             │          │
   attr set              │            │                │          │
                         │            │  check active  │          │
                         │            │  & scopes      │          │
                         │            │                │          │
                         │            │  bucket.       │          │
                         │            │  tryConsume(1) │          │
                         │            │ ◄──────────────┘          │
                         │            │                           │
                         │            │  log(cp, path,            │
                         │            │      status, duration)    │
                         │            │ ◄─────────────────────────┘
                         │            │
   Response ◄─────────────────────────────────────────────────────
```

#### Поток 3: Передача данных между компонентами

```
┌─────────────┐     ┌─────────────────────────────────────────────────────┐     ┌─────────────┐
│  Контрагент │     │                      NGINX                           │     │   GATEWAY   │
└──────┬──────┘     └──────────────────────┬──────────────────────────────┘     └──────┬──────┘
       │                                   │                                           │
       │  HTTPS                            │  HTTP (внутренняя сеть)                   │
       │  • Client cert (mTLS) + Bearer JWT│  • X-SSL-Client-DN, X-SSL-Client-Verify   │
       │  • Body, Headers                  │  • X-Forwarded-*, все заголовки           │
       │ ─────────────────────────────────►│ ─────────────────────────────────────────►│
       │                                   │                                           │
       │                                   │                              ┌────────────┴────────────┐
       │                                   │                              │  CounterpartyService    │
       │                                   │                              │  • CN → counterparty   │
       │                                   │                              │  • scopes, rate_limit   │
       │                                   │                              └────────────┬────────────┘
       │                                   │                                           │
       │                                   │  HTTP                                     │
       │                                   │  • X-Counterparty-Id                      │
       │                                   │  • X-Auth-Type                            │
       │                                   │  • Original headers, body                 │
       │                                   │                                           │
       │                                   │                                           ▼
       │                                   │                                    ┌─────────────┐
       │                                   │                                    │   BACKEND   │
       │                                   │                                    │  operations │
       │                                   │                                    │  documents  │
       │                                   │                                    └──────┬──────┘
       │                                   │                                           │
       │  Response body + headers          │  Response                                 │
       │ ◄─────────────────────────────────┼───────────────────────────────────────────┘
```

### Потоки аутентификации (сводка)

| Способ | Поток |
|--------|-------|
| **mTLS + JWT** | mTLS для транспорта + JWT для авторизации (scope, claims) |

### Роли компонентов

| Компонент | Роль |
|-----------|------|
| **Authorization Server** | Выдаёт JWT, JWK Set, управляет clients/пользователями |
| **Nginx** | TLS/mTLS termination, передача identity в заголовках |
| **Gateway** | Resource Server: валидация JWT, rate limit, маршрутизация, аудит |
| **Backend** | Бизнес-логика, получает X-Counterparty-Id |

Полный стек: **nginx-gateway/** — см. `nginx-gateway/README.md`

## Модули

| Модуль | Описание |
|--------|----------|
| **gateway-service** | Spring Cloud Gateway (mTLS+JWT, rate limit, audit) |
| **nginx-gateway** | Nginx с mTLS + Docker Compose |
| **kong-gateway** | Альтернатива на Kong (без OAuth) |

**Authorization Server** — внешний сервис (Keycloak, Spring Authorization Server, Auth0 и т.д.), в репозиторий не входит.

## Цепочка фильтров (Spring Boot)

1. **NginxMtlsIdentityFilter** — mTLS: identity из X-SSL-Client-DN (CN → counterparty)
2. **JwtValidationFilter** — JWT: валидация Bearer (scope, claims)
3. **CounterpartyContextFilter** — проверка контрагента
4. **RateLimitFilter** — ограничение по counterparty
5. **AuditLoggingFilter** — аудит запросов

## Требования

- Java 17+
- Maven 3.8+
- Docker (для nginx-gateway)

## Запуск

**Вариант 1 — Nginx + Gateway (рекомендуется):**
```bash
cd nginx-gateway
./scripts/generate-certs.ps1   # или generate-certs.sh
docker compose up -d
```

**Вариант 2 — только Gateway (для отладки):**  
Требуется Nginx для mTLS. Запуск без Nginx — только actuator, /api/** вернёт 401.

## Аутентификация (mTLS + JWT)

- **mTLS** — обязательно; в Nginx; CN передаётся в Gateway через X-SSL-Client-DN
- **JWT** — обязательно; Bearer token; `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

## Authorization Server

Поддерживаются любые OIDC-совместимые серверы:

- **Keycloak** — self-hosted, Client Credentials flow
- **Spring Authorization Server** — встраиваемый в Java-проект
- **Auth0, Okta, Azure AD** — облачные IdP

Контрагент получает JWT через `POST /oauth/token` (grant_type=client_credentials), затем использует его в заголовке `Authorization: Bearer <token>`.

## Маршруты

| Путь | Бэкенд |
|------|--------|
| `/api/v1/operations/**` | http://localhost:8081 |
| `/api/v1/documents/**` | http://localhost:8082 |
