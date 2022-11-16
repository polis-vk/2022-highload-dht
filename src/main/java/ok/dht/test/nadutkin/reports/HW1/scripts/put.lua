id = 0
wrk.method = "PUT"
request = function()
    wrk.path = "/v0/entity?id=" .. id
    wrk.body = "№ " .. id
    id = id + 1
    return wrk.format(nil)
end