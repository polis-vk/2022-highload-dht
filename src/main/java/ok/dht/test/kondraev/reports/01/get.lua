wrk.method = "GET"

function init(args)
    rs = require("randomstring")
    math.randomseed(os.time() ^ 13)
    size = args[1]
end

function request()
    wrk.path = "/v0/entity?id=k" .. rs.random_string(size)
    return wrk.format()
end
