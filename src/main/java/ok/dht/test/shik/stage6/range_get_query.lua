counter = 70000000 - 1

function request()
    counter = counter + 1
    start = counter
    end_ = counter + 100
    body = ""
    headers = {}
    headers["Connection"] = "keep-alive"
    headers["Host"] = "localhost:19234"
    return wrk.format("GET", "/v0/entities?start=" .. tostring(start) .. "&end=" .. tostring(end_), headers, body)
end