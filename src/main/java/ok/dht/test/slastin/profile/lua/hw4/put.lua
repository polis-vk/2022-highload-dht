wrk.method = "PUT"

function getValue()
    image = io.open("../data/image.png", "r")
    return image:read("*all")
end

wrk.body = getValue()

local seed = 1337
math.randomseed(seed)

local MIN_KEY = 1
local MAX_KEY = 2000000

local ACK = 2
local FROM = 2

request = function()
    return wrk.format(nil, "/v0/entity?id=" .. math.random(MIN_KEY, MAX_KEY) .. "&ack=" .. ACK .. "&from=" .. FROM)
end
