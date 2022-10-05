math.randomseed(os.time())
       

function request()
    return wrk.format("GET", "/v0/entity?id=" .. math.random(1, 1024 * 1024), {}, nil)
end