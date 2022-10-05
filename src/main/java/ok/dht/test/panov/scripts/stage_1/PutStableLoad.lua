request = function()
    id = math.random(1, 10000)
    path = "/v0/entity?id=" .. id
    headers = {}
    body = string.rep("aaaaaaaaaaaa", 100)
    return wrk.format("PUT", path, headers, body)
end
