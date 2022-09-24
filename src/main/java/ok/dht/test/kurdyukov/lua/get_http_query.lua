cnt = 0

request = function()
    path = "/v0/entity?id=" .. cnt
    wrk.method = "GET"
    cnt = cnt + 1
    return wrk.format(nil, path)
end

