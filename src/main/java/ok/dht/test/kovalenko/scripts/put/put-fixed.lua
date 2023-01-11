raw_path = "/v0/entity?id="
request = function()
    local r = math.random(1, 300000000)
    path = raw_path .. r
    wrk.body = r
    return wrk.format("PUT", path)
end
