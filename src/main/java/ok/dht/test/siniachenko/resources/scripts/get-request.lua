request = function()
    key = math.random(1000000)
    path = "/v0/entity?id=key-" .. key
    return wrk.format("GET", path)
end
