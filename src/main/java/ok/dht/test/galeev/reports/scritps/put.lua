cnt = 0
request = function()
    uri = "/v0/entity?id=K:" .. cnt
    wrk.body = "V:" .. cnt
    cnt = cnt + 1
    return wrk.format("PUT", uri)
end