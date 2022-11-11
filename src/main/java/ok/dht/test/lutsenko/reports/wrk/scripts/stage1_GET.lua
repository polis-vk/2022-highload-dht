counter = 0

request = function()
   path = "/v0/entity?id=k" .. counter
   wrk.method = "GET"
   counter = counter + 1
   return wrk.format(nil, path)
end
