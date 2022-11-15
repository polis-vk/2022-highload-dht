request_path = "/v0/entity?id="
max_value = 10000000

request = function()
    path = request_path .. tostring(math.random(0, max_value))
    record_content_prefix = tostring(math.random(0, max_value))
    return wrk.format("PUT", path, {"Content-Type: text/plain"}, record_content_prefix .. "record_content")
end