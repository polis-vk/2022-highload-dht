wrk.method = "PUT"

math.randomseed(os.time())

request = function()
    id = math.random(500000)
    wrk.body = "MyLittlePony" .. id
    path = "/v0/entity?id=" .. id
    return wrk.format(nil, path)
end
