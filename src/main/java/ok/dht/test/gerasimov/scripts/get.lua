raw_path = "/v0/entity?id="
count = 0
request = function()
    path = raw_path .. count
    count = count + 1
    return wrk.format("GET", path)
end