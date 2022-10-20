raw_path = "/v0/entity?id="
count = -1
request = function()
    count = count + 1
    path = raw_path .. count
    return wrk.format("PUT", path, {"Content-Type: text/plain"}, string.rep("highload is a best subject!", 322))
end