id = 0
request = function()
    wrk.method = "PUT"
    wrk.body = "value_" .. id
    
    path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end