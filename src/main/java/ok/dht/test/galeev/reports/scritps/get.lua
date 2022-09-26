cnt = 0
request = function()
    uri = string.format("/v0/entity?id=k%010d", cnt)
    cnt = cnt + 1
    return wrk.format("GET", uri, {})
end