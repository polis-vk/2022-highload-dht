request_path = "/v0/entity?id="

key = -1
request = function()
    key = key + 1
    path = request_path .. tostring(key)
    return wrk.format("GET", path)
end