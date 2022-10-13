local randomstring = {}

function random_char()
    -- printable ascii
    return string.char(math.random(32, 127))
end

function randomstring.random_string(n)
    local a = {}
    for i = 1, tonumber(n) do
        a[i] = random_char()
    end
    return table.concat(a)
end

return randomstring