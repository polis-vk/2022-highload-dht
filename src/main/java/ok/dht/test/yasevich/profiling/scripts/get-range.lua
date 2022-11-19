request = function()
    local entryLength = 6
    local rangeLength = 1000
    local from = math.random(0, math.pow(10, entryLength) - 1) .. ""
    local to = (from + rangeLength) .. ""
    if(string.len(to) > entryLength) then to = string.rep("9", entryLength)
    while string.len(from) < entryLength do
        from = "0" .. from
    end
    while string.len(to) < entryLength do
        to = "0" .. to
    end
    path = "/v0/entities?start=" .. from .. "&end=" .. to
    return "GET " .. path .. " HTTP/1.1\r\n" .. "Host: http://localhost:12353\r\n\r\n"
end
