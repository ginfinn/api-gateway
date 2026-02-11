-- Валидация JWT (RS256 или HS256)
-- JWT_PUBLIC_KEY_PATH — путь к PEM публичного ключа (RS256)
-- JWT_SECRET — секрет для HS256 (для тестирования)

local jwt = require("resty.jwt")
local _M = {}

local function get_public_key()
    local path = os.getenv("JWT_PUBLIC_KEY_PATH") or "/etc/nginx/jwt/public.pem"
    if not path or path == "" then return nil end
    local f = io.open(path, "r")
    if not f then
        return nil, "JWT public key not found at " .. path
    end
    local key = f:read("*a")
    f:close()
    return key
end

local function get_secret()
    return os.getenv("JWT_SECRET")
end

function _M.verify(token)
    if not token or token == "" then
        return false, "empty token"
    end

    local public_key = get_public_key()
    local secret = get_secret()

    if public_key and public_key ~= "" then
        -- RS256
        local jwt_obj = jwt:verify(public_key, token, 0)
        if jwt_obj.verified then
            return true
        end
        return false, jwt_obj.reason or "signature invalid"
    elseif secret and secret ~= "" then
        -- HS256 (для тестирования)
        local jwt_obj = jwt:verify(secret, token)
        if jwt_obj.verified then
            return true
        end
        return false, jwt_obj.reason or "signature invalid"
    else
        return false, "JWT_PUBLIC_KEY_PATH or JWT_SECRET not configured"
    end
end

return _M
