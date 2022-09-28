index = 0
request = function()
    path = "/v0/entity?id=" .. index
    wrk.method = "PUT"
    wrk.body = string.rep("value#" .. index, 100)
    index = index + 1
    return wrk.format(nil, path)
end