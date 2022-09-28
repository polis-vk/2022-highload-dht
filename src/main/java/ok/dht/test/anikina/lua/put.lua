counter = 0

request = function()
    path = "/v0/entity?id=keyNumber" .. counter
    wrk.method = "PUT"
    wrk.body = string.rep("very interesting string", 30)
    counter = counter + 1
    return wrk.format("PUT", path)
end
