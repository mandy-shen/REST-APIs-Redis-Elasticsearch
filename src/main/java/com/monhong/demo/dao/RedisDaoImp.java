package com.monhong.demo.dao;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Repository
public class RedisDaoImp implements RedisDao {

    private final StringRedisTemplate redisTemplate;

    public RedisDaoImp(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void deleteKeys(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    @Override
    public boolean deleteValue(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    @Override
    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void hDelete(String key, String field) {
        redisTemplate.opsForHash().delete(key, field);
    }

    @Override
    public void hDelete(String key) {
        redisTemplate.opsForHash().delete(key);
    }

    @Override
    public String hGet(String key, String field) {
        return (String) redisTemplate.opsForHash().get(key, field);
    }

    @Override
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    @Override
    public void hSet(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public Set<String> keys(String keyPattern) {
        return redisTemplate.keys(keyPattern);
    }

    @Override
    public void putValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public Set<String> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    @Override
    public void setAdd(String key, String value) {
        redisTemplate.opsForSet().add(key, value);
    }
}
