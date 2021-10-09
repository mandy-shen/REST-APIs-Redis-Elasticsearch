package com.monhong.demo.plan;

import org.apache.logging.log4j.util.Strings;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

import static com.monhong.demo.validator.Constant.OBJECT_ID;

@Repository
public class PlanRepository {

    private RedisTemplate<String, String> redisTemplate;
    private HashOperations<String, String, String> hashOperations;

    public PlanRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    public boolean hasObjKey(String objKey) {
        return hashOperations.hasKey(objKey, OBJECT_ID);
    }

    public boolean hasKey(String objKey, String key) {
        return hashOperations.hasKey(objKey, key);
    }

    public Map<String, String> get(String objKey) {
        return hashOperations.entries(objKey);
    }
    public String get(String objKey, String key) {
        String value = hashOperations.get(objKey, key);
        return value == null ? Strings.EMPTY : value;
    }

    public void put(String objKey, String key, String val) {
        hashOperations.put(objKey, key, val);
    }

    public void delete(String objKey, String key) {
        if (!hasKey(objKey, key))
            return;

        hashOperations.delete(objKey, key);
    }
}
