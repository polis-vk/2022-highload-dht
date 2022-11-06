request_path = "/v0/entity?id="
max_value = 100000000

request = function()
    path = request_path .. math.random(0, max_value)
    record_content_prefix = math.random(0, max_value)
    return wrk.format("PUT", path, {"Content-Type: text/plain"}, record_content_prefix .. "record_content")
end