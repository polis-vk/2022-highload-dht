id = 0
request = function()
    id = math.random(1,1000)
    return wrk.format("GET", "/v0/entity?id=" .. id, nil, nil)
end