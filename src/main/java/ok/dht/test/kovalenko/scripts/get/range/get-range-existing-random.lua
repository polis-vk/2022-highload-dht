raw_path = "/v0/entities"
start_arg = "?start="
end_arg = "&end="
start_point = 1
point_delta = 5
request = function()
    local start1 = math.random(start_point, start_point + point_delta)
    local end1 = math.random(start1, start_point + 2 * point_delta)
    start_point = start_point + point_delta
    path = table.concat({raw_path, start_arg, start1, end_arg, end1})
    return wrk.format("GET", path)
end
