raw_path = "/v0/entity?id=k00"
request = function()
    local r = math.random(10_000_000, 100_000_000-1)
    path = raw_path .. r
    return wrk.format("GET", path)
end
