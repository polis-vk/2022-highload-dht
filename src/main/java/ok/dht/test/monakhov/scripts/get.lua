counter = 0

request = function ()
    path = "/v0/entity?id=key" .. counter
    counter = counter + 1
    return wrk.format("GET", path)
end