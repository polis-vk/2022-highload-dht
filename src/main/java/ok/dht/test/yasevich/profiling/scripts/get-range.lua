request = function()
    local entryLength = 6
    local rangeLength = 10000
    local from = math.random(0, math.pow(10, entryLength) - 1)
    local to = from + rangeLength
    if to > math.pow(10, entryLength) - 1 then
        to = math.pow(10, entryLength) - 1
    end
    local startParam = tostring(from)
    local endParam = tostring(to)
    while string.len(startParam) < entryLength - 1 do
        startParam = "0" .. startParam
    end
    while string.len(endParam) < entryLength - 1 do
        endParam = "0" .. endParam
    end
    path = "/v0/entities?start=" .. startParam .. "&end=" .. endParam
    return "GET " .. path .. " HTTP/1.1\r\n" .. "Host: http://localhost:12353\r\n\r\n"
end
