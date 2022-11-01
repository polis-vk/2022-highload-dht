request = function()
    key = math.random(1, 100000000)
    path = "/v0/entity?id=k" .. key
    return wrk.format("GET", path)
end
