counter = 0

request = function()
    counterStr = counter .. ""
    counterStr = string.rep("0", 10 - string.len(counterStr)) .. counterStr
    path = "/v0/entity?id=" .. counterStr
    body = string.rep("string", 60)
    counter = counter + 1
    return "PUT " .. path .. " HTTP/1.1\r\nContent-Length: 360\r\n\r\n" .. body
end
