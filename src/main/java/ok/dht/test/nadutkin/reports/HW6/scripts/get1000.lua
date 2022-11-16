wrk.method = "GET"
request = function()
    start = math.random(0, 1000000)
    wrk.path = "/v0/entities?start=" .. start .. "&end=" .. start + 1000
    return wrk.format(nil)
end