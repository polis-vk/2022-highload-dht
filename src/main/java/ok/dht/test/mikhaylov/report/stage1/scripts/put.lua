index = 700000
request = function()
    path = "/v0/entity?id=" .. index
    wrk.method = "PUT"
    wrk.body = "value#" .. index
    index = index + 1
    return wrk.format(nil, path)
end