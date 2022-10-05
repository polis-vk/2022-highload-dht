wrk.method = "PUT"

function init(args)
    rs = require("randomstring")
    math.randomseed(os.time() ^ 13)
    key_size = args[1]
    value_size = args[2]
end

function request()
    wrk.path = "/v0/entity?id=k" .. rs.random_string(key_size)
    wrk.body = "v" .. rs.random_string(value_size)
    return wrk.format()
end
