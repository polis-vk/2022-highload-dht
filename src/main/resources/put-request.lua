path = "/v0/entity?id=key0"

request = function()
   wrk.body = "value"
   return wrk.format("PUT", path)
end

