id = 0
wrk.method = "PUT"
request = function()
    wrk.path = "/v0/entity?id=" .. math.random(0, 1000000) .. "&from=2&ack=1"
    wrk.body = "№ " .. id
    id = id + 1
    return wrk.format(nil)
end