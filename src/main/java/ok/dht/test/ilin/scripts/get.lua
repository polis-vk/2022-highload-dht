request = function()
    counter = math.random(1, 10000)
    path = "/v0/entity?id=" .. counter
    return "GET " .. path .. " HTTP/1.1\r\n" .. "Host: http://localhost:19234\r\n\r\n"
end
