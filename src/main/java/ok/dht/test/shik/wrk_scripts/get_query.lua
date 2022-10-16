counter = 110000000

function request()
    counter = counter + 1
    body = ""
    headers = {}
    headers["Connection"] = "keep-alive"
    headers["Host"] = "localhost:19234"
    return wrk.format("GET", "/v0/entity?id=" .. tostring(counter), headers, body)
end