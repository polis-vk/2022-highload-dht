counter = 0

request = function()
   path = "/v0/entity?id=" .. counter
   wrk.headers["X-Counter"] = counter
   wrk.method = "GET"
   wrk.body = "value" .. counter
   counter = counter + 1
   return wrk.format(nil, path)
end