wrk.method = "GET"
request = function()
    start = math.random(0, 1000000)
    finish = start + 10
    wrk.path = '/v0/entities?start=' .. start .. '&end=' .. finish
    return wrk.format(nil)
end