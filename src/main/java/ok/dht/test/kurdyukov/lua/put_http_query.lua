cnt = 0

request = function ()
    path = "/v0/entity?id=" .. cnt
    wrk.method = "PUT"
    wrk.headers["X-cnt"] = cnt
    wrk.body = string.rep("abacaba", 100) .. cnt
    cnt = cnt + 1
    return wrk.format(nil, path)
end
