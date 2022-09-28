counter = 0

request = function()
    path = "/v0/entity?id=keyNumber" .. counter
    counter = counter + 1
    return "GET " .. path .. " HTTP/1.1\r\nContent-Length: 0\r\n\r\n"
end
