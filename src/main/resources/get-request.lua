path = "/v0/entity?id=key0"

request = function()
   return wrk.format("GET", path)
end

