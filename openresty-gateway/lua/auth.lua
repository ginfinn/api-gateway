-- mTLS + JWT: аутентификация и авторизация
local jwt_verify = require("jwt_verify")
local counterparty = require("counterparty")

local _M = {}

function _M.access()
    -- 1. mTLS: проверка клиентского сертификата
    local ssl_verify = ngx.var.ssl_client_verify
    if ssl_verify ~= "SUCCESS" then
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.say('{"error":"mTLS required"}')
        return ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    local ssl_dn = ngx.var.ssl_client_s_dn
    if not ssl_dn or ssl_dn == "" then
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.say('{"error":"Client certificate CN required"}')
        return ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    -- 2. Извлечение CN из DN
    local cn = _M.parse_cn(ssl_dn)
    if not cn then
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.say('{"error":"Invalid certificate DN"}')
        return ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    -- 3. Маппинг CN → counterparty
    local cp = counterparty.get(cn)
    if not cp then
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.say('{"error":"Unknown counterparty: ' .. cn .. '"}')
        return ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    if not cp.active then
        ngx.status = ngx.HTTP_FORBIDDEN
        ngx.say('{"error":"Counterparty inactive"}')
        return ngx.exit(ngx.HTTP_FORBIDDEN)
    end

    -- 4. JWT: проверка Bearer токена
    local auth_header = ngx.var.http_authorization
    if not auth_header or not auth_header:match("^Bearer ") then
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.say('{"error":"Bearer token required"}')
        return ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    local token = auth_header:sub(8):match("^%s*(.-)%s*$")
    local jwt_ok, jwt_err = jwt_verify.verify(token)
    if not jwt_ok then
        ngx.status = ngx.HTTP_UNAUTHORIZED
        ngx.say('{"error":"Invalid JWT: ' .. (jwt_err or "unknown") .. '"}')
        return ngx.exit(ngx.HTTP_UNAUTHORIZED)
    end

    -- 5. Rate limit key (для limit_req_zone)
    ngx.var.limit_key = cp.id

    -- 6. Установка переменных для proxy
    ngx.var.counterparty_id = cp.id

    -- 7. Audit (логируется в access_log через log_format)
end

function _M.parse_cn(dn)
    for part in dn:gmatch("[^,]+") do
        part = part:match("^%s*(.-)%s*$")
        if part:upper():sub(1, 3) == "CN=" then
            return part:sub(4):match("^%s*(.-)%s*$")
        end
    end
    return nil
end

return _M
