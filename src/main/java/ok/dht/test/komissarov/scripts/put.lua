k = 0;

request = function()
    path = "/v0/entity?id=k" .. k
    wrk.method = "PUT"
    wrk.body = "v" .. k
    k = k + 1
    return wrk.format(nil, path)
end