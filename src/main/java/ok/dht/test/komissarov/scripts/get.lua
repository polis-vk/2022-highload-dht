key = 0

request = function()
    path = "/v0/entity?id=key" .. key
    key = key + 1
    wrk.method = "GET"
    wrk.body = "value"
    return wrk.format(nil, path)
end