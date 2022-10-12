-- Скрипт для put запросов --
-- wrk2 -c 1 -d 60s -t 1 -R 20000 -L -s put_script.lua http://localhost:12345 - так проверял на стабильной нагрузке--
-- wrk2 -c 1 -d 40m -t 1 -R 22000 -s put_script.lua http://localhost:12345 - вот так можно заполнить базу на 2.3 гига --
id = -1
request = function()
    id = id + 1
    return wrk.format("PUT", "/v0/entity?id=" .. id, nil, id)
end
