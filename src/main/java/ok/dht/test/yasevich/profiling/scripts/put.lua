request = function()
    local id = math.random(0, 25000000)
    local value = math.random(0, 25000000)
    path = "/v0/entity?id=key" .. id
    wrk.method = "PUT"
    wrk.body = "value" .. value
    return "PUT " .. path .. " HTTP/1.1\r\n" .. "Host: http://localhost:19234\r\nContent-Length: " .. string.len(tostring(value)) .. "\r\n\r\n" .. tostring(value)
end
