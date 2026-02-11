# OpenResty Gateway — mTLS + JWT

Полная реализация API Gateway на OpenResty (Nginx + Lua): mTLS, JWT, rate limiting, маршрутизация. Эквивалент Spring Cloud Gateway без JVM.

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
│  • POST /oauth/token  (client_id, client_secret)             │
│  • Выдаёт JWT access token                                  │
│  • JWK Set URI / публичный ключ (PEM)                       │
└──────────────────────────┬──────────────────────────────────┘
                           │ JWT
        ┌──────────────────┼──────────────────────────────────┐
        │                  │  Запрос (mTLS + Bearer JWT)       │
        │                  ▼                                  │
        │  ┌────────────────────────────────────────────────┐ │
        │  │  OPENRESTY (Nginx + Lua)                        │ │
        │  │  • mTLS (ssl_verify_client)                     │ │
        │  │  • Lua: CN → counterparty, JWT validation       │ │
        │  │  • limit_req (rate limit по counterparty)       │ │
        │  │  • Маршрутизация → backend                      │ │
        │  └────────────────────┬───────────────────────────┘ │
        └───────────────────────┼─────────────────────────────┘
                                │ X-Counterparty-Id
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    BACKEND-СЕРВИСЫ                          │
│  operations-service (8081)    documents-service (8082)       │
└─────────────────────────────────────────────────────────────┘
```

### Поток данных (запрос)

```
Контрагент       Auth Server        OpenResty                    Backend
    │                 │                  │                           │
    │  POST /oauth/token                 │                           │
    │ ───────────────►│                  │                           │
    │  200 {access_token}                │                           │
    │ ◄────────────────                  │                           │
    │                 │                  │                           │
    │  GET /api/v1/operations/           │                           │
    │  + client cert + Bearer JWT        │                           │
    │ ──────────────────────────────────►│                           │
    │                   │  mTLS verify   │                           │
    │                   │  CN parse      │                           │
    │                   │  counterparty  │                           │
    │                   │  JWT verify    │                           │
    │                   │  limit_req     │                           │
    │                   │  + X-Counterparty-Id                       │
    │                   │ ─────────────────────────────────────────►│
    │  200 OK           │ ◄─────────────────────────────────────────│
    │ ◄─────────────────│                  │                           │
```

### Цепочка обработки (один запрос)

```
Request → rewrite_by_lua (auth.lua) → limit_req → proxy_pass → Backend
              │
              ├─ mTLS: ssl_client_verify, ssl_client_s_dn
              ├─ CN parse → counterparty.get(cn)
              ├─ JWT: Bearer token → jwt_verify.verify()
              ├─ ngx.var.limit_key = cp.id
              └─ ngx.var.counterparty_id = cp.id
```

## Требования

- Docker, Docker Compose
- OpenSSL (для генерации сертификатов)

## Быстрый старт

### 1. Сертификаты

```powershell
# Windows
.\scripts\generate-certs.ps1
```

```bash
# Linux/macOS
chmod +x scripts/generate-certs.sh
./scripts/generate-certs.sh
```

### 2. JWT — настройка

**Вариант A: RS256 (production)** — публичный ключ из Authorization Server

```bash
mkdir -p nginx/jwt
# Экспорт публичного ключа из Keycloak:
# Realm → Keys → RS256 → Public key → скопировать в public.pem
# Или: openssl x509 -in cert.pem -pubkey -noout > public.pem
```

Положите `public.pem` в `nginx/jwt/`. Путь по умолчанию: `/etc/nginx/jwt/public.pem`.

**Вариант B: HS256 (тестирование)** — без Auth Server

В `docker-compose.yml`:
```yaml
environment:
  JWT_SECRET: "test-secret-at-least-32-characters"
```

Сгенерируйте JWT на [jwt.io](https://jwt.io):
- Algorithm: HS256
- Payload: `{"sub":"cp-a-001","exp":9999999999}`
- Secret: тот же, что в JWT_SECRET

### 3. Запуск

```bash
docker compose up -d
```

- **https://localhost:8443** — API Gateway
- **/health** — health check (без auth)

### 4. Тест

```bash
# С JWT от Authorization Server или jwt.io
curl -k --cert nginx/certs/client-cp-a.crt --key nginx/certs/client-cp-a.key \
  -H "Authorization: Bearer YOUR_JWT" \
  https://localhost:8443/api/v1/operations/
```

## Структура проекта

```
openresty-gateway/
├── Dockerfile
├── docker-compose.yml
├── nginx/
│   ├── nginx.conf      # mTLS, limit_req, proxy, upstreams
│   ├── certs/          # CA, server, client certs (generate-certs)
│   └── jwt/            # public.pem (RS256) — опционально
├── lua/
│   ├── auth.lua        # mTLS + JWT, CN → counterparty, 401/403
│   ├── counterparty.lua # Маппинг CN → id, scopes, rate_limit
│   └── jwt_verify.lua  # Валидация Bearer (RS256/HS256)
└── scripts/
    ├── generate-certs.ps1
    └── generate-certs.sh
```

## Конфигурация

| Компонент | Путь | Описание |
|-----------|------|----------|
| Nginx | nginx/nginx.conf | mTLS, limit_req, proxy, upstreams |
| Lua auth | lua/auth.lua | mTLS + JWT проверка |
| Counterparty | lua/counterparty.lua | CN → id, scopes, rate_limit |
| JWT | lua/jwt_verify.lua | Валидация Bearer токена |

## Контрагенты (stub)

| CN | id | rate_limit | scopes |
|----|-----|------------|--------|
| cp-a-001 | stub-cp-a-001 | 100/min | operations:read, documents:* |
| cp-b-002 | stub-cp-b-002 | 100/min | operations:read, documents:read |
| cp-c-003 | stub-cp-c-003 | 50/min | documents:read |

Для production — загрузка из Redis/API в `counterparty.lua`.

## Маршруты

| Путь | Backend |
|------|---------|
| `/api/v1/operations/` | host.docker.internal:8081 |
| `/api/v1/documents/` | host.docker.internal:8082 |

Измените `upstream operations` и `upstream documents` в `nginx/nginx.conf`.

## Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| JWT_PUBLIC_KEY_PATH | Путь к PEM публичного ключа (RS256) | /etc/nginx/jwt/public.pem |
| JWT_SECRET | Секрет для HS256 (тестирование) | — |

## Заголовки для Backend

- `X-Counterparty-Id` — идентификатор контрагента
- `X-Auth-Type` — MTLS_OAUTH
- `X-Real-IP`, `X-Forwarded-*` — стандартные proxy-заголовки

## Production

- Замените stub в `counterparty.lua` на загрузку из Redis/API (lua-resty-redis, lua-resty-http)
- Используйте RS256 с публичным ключом из JWKS (обновление при ротации ключей)
- Настройте `limit_req` под реальные лимиты контрагентов (сейчас 100/min глобально)
- Для кластера — распределённый rate limiting (Redis + lua-resty-lock)

## Сравнение с Spring Gateway

| Функция | OpenResty | Spring Cloud Gateway |
|---------|-----------|----------------------|
| mTLS | Nginx ssl_verify_client | Nginx + X-SSL-* |
| JWT | lua-resty-jwt | JwtDecoder |
| Rate limit | limit_req_zone | Bucket4j |
| Counterparty | Lua table / Redis | CounterpartyService + БД |
| Память | ~20–50 MB | ~256+ MB |
| Стек | Lua, Nginx | Java, Netty |
