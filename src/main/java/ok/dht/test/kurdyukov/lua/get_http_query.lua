cnt = 0

request = function()
    path = "/v0/entity?id=" .. math.random(1, 10000)
    wrk.method = "GET"
    cnt = cnt + 1
    return wrk.format(nil, path)
end

