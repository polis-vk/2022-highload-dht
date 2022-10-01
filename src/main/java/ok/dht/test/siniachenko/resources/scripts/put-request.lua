request = function()
    key = math.random(10000000)
    value = math.random(10000000)
    path = "/v0/entity?id=key-" .. key
    wrk.body = "value-" .. value
    return wrk.format("PUT", path)
end
