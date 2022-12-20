request_path = "/v0/entity?id="
max_value = 100000000
math.randomseed(os.time())

insert_key = -1
request = function()
    insert_key = insert_key + 1
    path = request_path .. insert_key
    record_content_prefix = math.random(0, max_value)
    return wrk.format("PUT", path, {"Content-Type: text/plain"}, record_content_prefix .. "record_content")
end