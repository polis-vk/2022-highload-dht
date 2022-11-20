id = 0;
request = function()
    id = id + 1;
    return wrk.format("GET", "/v0/entity?id=" .. id, headers, body);
end