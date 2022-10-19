id = 0
request = function()
    id = math.random(1000000,50000000)
    local res = ""
    for i = 1, math.random(400,800) do
        res = res .. 's'
    end
    return wrk.format("PUT", "/v0/entity?id=" .. id, nil, res)
end