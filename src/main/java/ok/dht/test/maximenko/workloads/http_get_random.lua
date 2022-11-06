request_path = "/v0/entity?id="
max_value = 50000
math.randomseed(os.time())

request = function()
    path = request_path .. tostring(math.random(0, max_value))
    return wrk.format("GET", path)
end