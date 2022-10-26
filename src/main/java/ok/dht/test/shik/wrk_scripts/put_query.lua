counter = 135000000

function request()
    counter = counter + 1
    body = tostring(counter)
    headers = {}
    headers["Content-Type"] = "text/plain"
    headers["Content-Length"] = #{string.byte(body, 1, -1)}
    headers["Connection"] = "keep-alive"
    headers["Host"] = "localhost:19234"
    return wrk.format("PUT", "/v0/entity?id=" .. tostring(counter), headers, body)
end