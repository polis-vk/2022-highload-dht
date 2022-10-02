math.randomseed(os.time())

function generate_random_string(n)
    local alphabet = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM"
    local result = {}

    for _ = 1, n do
        local alphabet_index = math.random(1, #alphabet)
        table.insert(result, alphabet:sub(alphabet_index, alphabet_index))
    end

    return table.concat(result)
end

wrk.host = "localhost"
wrk.port = "8080"

function request()
    wrk.body = generate_random_string(math.random(100, 200))

    local id
    if math.random(0, 1) == 0 then
        id = tostring(math.random(0, 6 * (10 ^ 7)))
    else
        id = generate_random_string(math.random(3, 10))
    end

    local path = "/v0/entity?id=" .. id
    return wrk.format("PUT", path)
end
