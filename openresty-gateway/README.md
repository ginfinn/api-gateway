# OpenResty Gateway — mTLS + JWT

Полная реализация API Gateway на OpenResty (Nginx + Lua): mTLS, JWT, rate limiting, маршрутизация.

## Архитектура

```
Контрагент (client cert + Bearer JWT)
        │
        ▼
┌─────────────────────────────────────────────────────┐
│  OpenResty (Nginx + Lua)                             │
│  • mTLS (ssl_verify_client)                         │
│  • Lua: CN → counterparty, JWT validation           │
│  • limit_req (rate limit по counterparty)           │
│  • Маршрутизация → backend                          │
└─────────────────────────────────────────────────────┘
        │
        ▼
  Backend-сервисы
```

## Требования

- Docker, Docker Compose
- OpenSSL (для сертификатов)

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

### 2. JWT — публичный ключ (RS256)

Извлеките публичный ключ из JWKS вашего Authorization Server и сохраните в PEM:

```bash
mkdir -p nginx/jwt
# Пример: экспорт из Keycloak или openssl
# Положите public.pem в nginx/jwt/
```

Для **тестирования** без Auth Server можно использовать HS256:

```yaml
# docker-compose.yml
environment:
  JWT_SECRET: "test-secret-at-least-32-chars"
```

И сгенерировать тестовый JWT (например, через jwt.io с alg=HS256).

### 3. Запуск

```bash
docker compose up -d
```

- **https://localhost:8443** — API Gateway

### 4. Тест

**С JWT от Authorization Server:**
```bash
curl -k --cert nginx/certs/client-cp-a.crt --key nginx/certs/client-cp-a.key \
  -H "Authorization: Bearer YOUR_JWT" \
  https://localhost:8443/api/v1/operations/
```

**Тест HS256 (JWT_SECRET в docker-compose):** сгенерируйте JWT на [jwt.io](https://jwt.io) (alg=HS256, payload: `{"sub":"cp-a-001","exp":9999999999}`).

## Конфигурация

| Компонент | Путь | Описание |
|-----------|------|----------|
| Nginx | nginx/nginx.conf | mTLS, limit_req, proxy |
| Lua auth | lua/auth.lua | mTLS + JWT проверка |
| Counterparty | lua/counterparty.lua | CN → id, scopes, rate_limit |
| JWT | lua/jwt_verify.lua | Валидация Bearer токена |

## Контрагенты (stub)

| CN | id | rate_limit |
|----|-----|------------|
| cp-a-001 | stub-cp-a-001 | 100/min |
| cp-b-002 | stub-cp-b-002 | 100/min |
| cp-c-003 | stub-cp-c-003 | 50/min |

## Переменные окружения

| Переменная | Описание |
|------------|----------|
| JWT_PUBLIC_KEY_PATH | Путь к PEM публичного ключа (RS256) |
| JWT_SECRET | Секрет для HS256 (тестирование) |

## Backend

По умолчанию: `host.docker.internal:8081`. Измените `upstream backend` в `nginx.conf` для своего окружения.
