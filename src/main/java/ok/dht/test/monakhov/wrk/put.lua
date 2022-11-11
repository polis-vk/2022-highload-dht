counter = 0

request = function ()
    path = "/v0/entity?id=key" .. counter
    wrk.body = string.rep("Get me out of this hell!", 500)
    counter = counter + 1
    return wrk.format("PUT", path)
end