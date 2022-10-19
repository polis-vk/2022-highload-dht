cnt = 0
request = function()
    uri = "/v0/entity?id=K:" .. cnt
    cnt = cnt + 1
    return wrk.format("GET", uri, {})
end