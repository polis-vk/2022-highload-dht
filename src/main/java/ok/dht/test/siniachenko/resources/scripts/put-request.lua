request = function()
    key = math.random(1000000)
    value = math.random(1000000)
    path = "/v0/entity?id=key-" .. key .. "&ack=3&from=3"
    wrk.body = "value-" .. value
    return wrk.format("PUT", path)
end
