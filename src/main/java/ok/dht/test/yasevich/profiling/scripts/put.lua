request = function()
    local id = math.random(0,50000000)
    path = "/v0/entity?id=key" .. id
    wrk.method = "PUT"
    wrk.body = "VALUE" .. id
    return wrk.format(nil, path, headers)
end
