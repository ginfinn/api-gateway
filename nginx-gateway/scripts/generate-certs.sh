#!/bin/bash
# Генерация тестовых сертификатов для mTLS
# Только для разработки! В production используйте PKI организации.

set -e
CERTS_DIR="$(cd "$(dirname "$0")/../nginx/certs" && pwd)"
mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

# CA
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt \
  -subj "/CN=Bank-API-Gateway-CA"

# Сервер
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr \
  -subj "/CN=localhost"
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 3650 -sha256
rm server.csr

# Клиент 1 (counterparty-a)
openssl genrsa -out client-cp-a.key 2048
openssl req -new -key client-cp-a.key -out client-cp-a.csr \
  -subj "/CN=cp-a-001/O=CounterpartyA"
openssl x509 -req -in client-cp-a.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client-cp-a.crt -days 3650 -sha256
rm client-cp-a.csr

# Клиент 2 (counterparty-b)
openssl genrsa -out client-cp-b.key 2048
openssl req -new -key client-cp-b.key -out client-cp-b.csr \
  -subj "/CN=cp-b-002/O=CounterpartyB"
openssl x509 -req -in client-cp-b.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client-cp-b.crt -days 3650 -sha256
rm client-cp-b.csr

echo "Certificates generated in $CERTS_DIR"
ls -la "$CERTS_DIR"
