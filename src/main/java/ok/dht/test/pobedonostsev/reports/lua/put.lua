request = function()
    key = math.random(0, 100000000)
    path = "/v0/entity?id=k" .. key
    body = "v" .. key
    return wrk.format("PUT", path, nil, body)
end
