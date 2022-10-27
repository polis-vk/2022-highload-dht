math.randomseed(19102022)

cnt = 1

function request()
    cnt = cnt + 1
    return wrk.format("GET", "/v0/entity?id=" .. cnt, {}, nil)
end