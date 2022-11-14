request = function()
     local id = math.random(0, 25000000)
     path = "/v0/entity?id=key" .. id .. "&replicas=1/1"
     return "GET " .. path .. " HTTP/1.1\r\n" .. "Host: http://localhost:12353\r\n\r\n"
 end
