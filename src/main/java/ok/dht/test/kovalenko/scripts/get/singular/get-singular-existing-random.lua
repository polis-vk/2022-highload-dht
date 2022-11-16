raw_path = "/v0/entity?id="
request = function()
    local r = math.random(1, 100000000)
    path = raw_path .. r
    return wrk.format("GET", path)
end
