math.randomseed(os.time())

wrk.host = "localhost"
wrk.port = "8080"

function request()
    local path = "/v0/entity?id=" .. tostring(math.random(0, 75 * (10 ^ 6)))
    return wrk.format("GET", path)
end
