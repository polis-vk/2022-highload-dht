id = 0
local byteSize = 10

request = function()
    wrk.method = "PUT"
    wrk.body = createRandomString(byteSize)

    path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end

function createRandomString(length)
    local res = ""
    for i = 1, length do
        math.randomseed(os.clock() ^ 5)
        res = res .. string.char(math.random(97, 122));
    end

    return res
end