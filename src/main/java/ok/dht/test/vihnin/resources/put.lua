math.randomseed(os.time())
       



function request()
    return wrk.format(
        "PUT", 
        "/v0/entity?id=" .. math.random(1, 1024 * 1024), 
        {}, 
        string.rep("data", 100)
    )
end