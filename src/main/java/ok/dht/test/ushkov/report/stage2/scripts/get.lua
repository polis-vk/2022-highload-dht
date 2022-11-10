counter = 0
request = function()
    wrk.method = "GET"
    path = "/v0/entity?id=" .. counter
    counter = counter + 1
    return wrk.format(nil, path)
end