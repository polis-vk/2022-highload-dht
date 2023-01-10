math.randomseed(19102022)

cnt = 1

function request()
    cnt = cnt + 1
    return wrk.format("GET", "/v0/entity?id=" .. math.random(1000000, 4000000), {}, nil)
end