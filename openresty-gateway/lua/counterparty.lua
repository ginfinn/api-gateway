-- Маппинг CN (из сертификата) → counterparty
-- В продакшене: загрузка из Redis/API

local _M = {}

local STUB_DATA = {
    ["cp-a-001"] = {
        id = "stub-cp-a-001",
        external_id = "cp-a-001",
        rate_limit = 100,
        scopes = {"operations:read", "documents:read", "documents:write"},
        active = true,
    },
    ["cp-b-002"] = {
        id = "stub-cp-b-002",
        external_id = "cp-b-002",
        rate_limit = 100,
        scopes = {"operations:read", "documents:read"},
        active = true,
    },
    ["cp-c-003"] = {
        id = "stub-cp-c-003",
        external_id = "cp-c-003",
        rate_limit = 50,
        scopes = {"documents:read"},
        active = true,
    },
}

function _M.get(cn)
    if not cn or cn == "" then
        return nil
    end
    return STUB_DATA[cn]
end

return _M
