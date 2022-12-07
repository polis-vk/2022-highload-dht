id = 0;
request = function()
    local value = string.char(math.random(65, 90));
    id = id + 1;
    return wrk.format("PUT", "/v0/entity?id=" .. id, headers, value);
end