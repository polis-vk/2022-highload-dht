cnt = 0

request = function()
    url = "/v0/entity?id=" .. math.random(1, 5000)
    body = 'smth' .. cnt
    cnt = cnt + 1
    return wrk.format("PUT", url, {}, body)
end
