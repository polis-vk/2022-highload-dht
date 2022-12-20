singleRandomValue = nil

init = function(args)
    local value = "";
    for _ = 0,20000,1
    do
        value = value .. string.char(math.random(32,126));
    end
    singleRandomValue = value
end

request = function()
    id = math.random(1, 20000) + math.random(1, 20000);
    return wrk.format("PUT", "/v0/entity?id=" .. id, headers, singleRandomValue);
end