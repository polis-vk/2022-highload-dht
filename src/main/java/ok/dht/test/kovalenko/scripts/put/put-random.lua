math.randomseed(os.time())

local charset = {}  do -- [0-9a-zA-Z]
    for c = 48, 57  do table.insert(charset, string.char(c)) end
    for c = 65, 90  do table.insert(charset, string.char(c)) end
    for c = 97, 122 do table.insert(charset, string.char(c)) end
end

local function randomString(length)
    if not length or length <= 0 then return '' end
    return randomString(length - 1) .. charset[math.random(1, #charset)]
end

raw_path = "/v0/entity?id="
request = function()
    math.randomseed(os.clock()^5)
    local r = math.random(1, 10000)
    key = randomString(r)
    value = randomString(r)
    path = raw_path .. key
    wrk.body = value
    return wrk.format("PUT", path)
end
