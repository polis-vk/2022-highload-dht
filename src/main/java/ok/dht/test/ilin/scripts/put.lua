counter = 0
request = function()
    counter = counter + 1
    path = "/v0/entity?id=" .. counter
    return "PUT " .. path .. " HTTP/1.1\r\n" .. "Host: http://localhost:19234\r\nContent-Length: " .. string.len(tostring(counter)) .. "\r\n\r\n" .. tostring(counter)
end