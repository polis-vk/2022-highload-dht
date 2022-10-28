-- База заполнена снепшотом с размером 2.4 гб
-- wrk2 -c 1 -d 60s -t 1 -R 20000 -L -s get_script.lua http://localhost:12345 - так проверял на стабильной нагрузке
id = -1
request = function()
    id = id + 1
    return wrk.format("GET", "/v0/entity?id=" .. id, nil, nil)
end
