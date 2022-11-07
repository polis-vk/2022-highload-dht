request = function()
    id = math.random(1, 10000)
    path = "/v0/entity?id=" .. id
    return wrk.format("GET", path)
end
