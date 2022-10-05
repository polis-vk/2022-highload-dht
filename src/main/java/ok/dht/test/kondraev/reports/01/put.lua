local bit = require("bit")
function init()
    math.randomseed(os.time() ^ 13)
end

wrk.method = "PUT"
wrk.path = "/v0/entity?id=k" .. math.random(bit.lshift(1, 16))
wrk.body = "v" .. math.random(bit.lshift(1, 32))
