counter = 0
request = function()
    wrk.method = "PUT"
    wrk.body = "" .. counter
    path = "/v0/entity?id=key_" .. counter .. "&ack=2&from=3"
    counter = counter + 1
    return wrk.format(nil, path)
end