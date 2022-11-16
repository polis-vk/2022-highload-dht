from = 0
to = 50

request = function()
    fromStr = from .. ""
    toStr = to .. ""
    fromStr = string.rep("0", 10 - string.len(fromStr)) .. fromStr
    toStr = string.rep("0", 10 - string.len(toStr)) .. toStr
    path = "/v0/entities?start=" .. fromStr .. "&end=" .. toStr
    from = from + 1
    to = to + 1
    return "GET " .. path .. " HTTP/1.1\r\nContent-Length: 0\r\n\r\n"
end
