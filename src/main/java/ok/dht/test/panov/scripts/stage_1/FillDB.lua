count = 0
request = function()
    path = "/v0/entity?id=" .. count
    headers = {}
    body = string.rep("aaaaaaaaaaaa", 1000)
    count = count + 1
    return wrk.format("PUT", path, headers, body)
end