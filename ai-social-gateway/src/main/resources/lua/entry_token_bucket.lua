local key = KEYS[1]

local now = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refillTokens = tonumber(ARGV[3])
local refillPeriodMs = tonumber(ARGV[4])
local requestTokens = tonumber(ARGV[5])
local ttlMs = tonumber(ARGV[6])

local tokenField = 'tokens'
local timestampField = 'ts'

local data = redis.call('HMGET', key, tokenField, timestampField)
local tokens = tonumber(data[1])
local ts = tonumber(data[2])

if tokens == nil then
    tokens = capacity
end
if ts == nil then
    ts = now
end

if now > ts then
    local delta = now - ts
    local toAdd = math.floor(delta * refillTokens / refillPeriodMs)
    if toAdd > 0 then
        tokens = math.min(capacity, tokens + toAdd)
        ts = now
    end
end

local allowed = 0
local retryAfterMs = 0
if tokens >= requestTokens then
    tokens = tokens - requestTokens
    allowed = 1
else
    local need = requestTokens - tokens
    retryAfterMs = math.ceil(need * refillPeriodMs / refillTokens)
end

redis.call('HSET', key, tokenField, tokens, timestampField, ts)
redis.call('PEXPIRE', key, ttlMs)

local remaining = math.floor(tokens / 1000)
local retryAfterSeconds = math.ceil(retryAfterMs / 1000)

return { allowed, remaining, retryAfterSeconds }
