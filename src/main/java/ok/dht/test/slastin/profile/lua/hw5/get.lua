wrk.method = "GET"

local seed = 228
math.randomseed(seed)

local MIN_KEY = 1
local MAX_KEY = 2000000

local ACK = 2
local FROM = 3

request = function()
    return wrk.format(nil, "/v0/entity?id=" .. math.random(MIN_KEY, MAX_KEY) .. "&ack=" .. ACK .. "&from=" .. FROM)
end
