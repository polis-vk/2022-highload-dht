key = 0;

request = function()
    path = "/v0/entity?id=key" .. key
    key = key + 1
    wrk.method = "PUT"
    wrk.body = "value"
    return wrk.format(nil, path)
end