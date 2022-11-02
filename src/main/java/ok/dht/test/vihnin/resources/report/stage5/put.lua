math.randomseed(19102022)

cnt = 1

function rnd_str(length)
	local res = ""
	for i = 1, length do
		res = res .. string.char(math.random(97, 122))
	end
	return res
end

function request()
    cnt = cnt + 1
    return wrk.format(
        "PUT", 
        "/v0/entity?id=" .. cnt,
        {}, 
        string.rep(rnd_str(10), 500)
    )
end