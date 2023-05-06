#!lua name=thingylib
local function remove_script_in_queue(keys, args)
    local key = keys[1]
    local script_id = args[1]
    local score = tonumber(redis.call('ZINCRBY', key, -1, script_id))
    if score < 0 then
        redis.call('ZREM', key, script_id)
    end
    return score
end

redis.register_function('remove_script_in_queue', remove_script_in_queue)

local function get_and_move_doc(keys, args)
    local todo = keys[1]
    local doing = keys[2]
    local doc_id = redis.call('LRANGE', todo, 0, 0)[1]
    if doc_id ~= nil then
        redis.call('LTRIM', todo, 1, -1)
        redis.call('LPUSH', doing, doc_id)
    end
    return doc_id
end

redis.register_function('get_and_move_doc', get_and_move_doc)