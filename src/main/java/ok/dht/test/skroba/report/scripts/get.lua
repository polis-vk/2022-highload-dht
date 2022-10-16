request = function()
    url = "/v0/entity?id=" .. math.random(1, 3000)
    return wrk.format("GET", url)
end
