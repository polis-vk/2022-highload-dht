raw_path = "/v0/entity?id=k37500000"
request = function()
    return wrk.format("GET", raw_path)
end
