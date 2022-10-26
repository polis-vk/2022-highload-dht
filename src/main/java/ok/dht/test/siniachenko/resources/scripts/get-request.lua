request = function()
    key = math.random(1000000)
    path = "/v0/entity?id=key-" .. key .. "&ack=3&from=3"
    return wrk.format("GET", path)
end
