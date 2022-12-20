request = function()
    id = math.random(1, 20000);
    return wrk.format("GET", "/v0/entity?id=" .. id, headers, body);
end