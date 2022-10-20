raw_path = "/v0/entity?id=k"
request = function()
    local r = math.random(1, 100000000)
    path = raw_path .. r
    wrk.body = "v" .. r
    return wrk.format("PUT", path)
end
