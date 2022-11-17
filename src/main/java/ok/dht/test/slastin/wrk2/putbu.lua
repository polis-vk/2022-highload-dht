wrk.method = "PUT"

math.randomseed(os.time())

request = function()
    id = math.random(5000000000000)
    wrk.body = string.rep("MiyLittlePony", 5000) .. id
    path = "/v0/entity?id=" .. id
    return wrk.format(nil, path)
end
