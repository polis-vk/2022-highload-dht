counter = 0

request = function()
    path = "/v0/entity?id=" .. counter
    body = string.rep("string", 60)
    counter = counter + 1
    return "PUT " .. path .. " HTTP/1.1\r\nContent-Length: 360\r\n\r\n" .. body
end
