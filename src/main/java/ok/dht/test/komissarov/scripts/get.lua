key = 0

request = function()
    path = "/v0/entity?id=k" .. key
    wrk.method = "GET"
    wrk.body = "v" .. key
    key = key + 1
    return wrk.format(nil, path)
end