raw_path = "/v0/entity?id=50000000"
request = function()
    return wrk.format("GET", raw_path)
end
