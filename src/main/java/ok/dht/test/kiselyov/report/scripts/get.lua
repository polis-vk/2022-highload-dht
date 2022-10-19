math.randomseed(os.time())
request = function()
    local id = math.random(0,999999)
    path = "/v0/entity?id=" .. id
    return wrk.format("GET", path, {"Content-Type: text/plain"})
end