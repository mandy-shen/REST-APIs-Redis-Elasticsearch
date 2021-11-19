package com.mandy.demo.dao;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface RedisDao {
    // normal
    void deleteKey(String key);

    // normal
    void deleteKeys(Collection<String> keys);

    // normal
    boolean deleteValue(String key);

    // normal
    String getValue(String key);

    // hash (hashmap)
    void hDelete(String key, String field);

    // hash (hashmap)
    void hDelete(String key);

    // hash (hashmap)
    String hGet(String key, String field);

    // hash (hashmap)
    Map<Object, Object> hGetAll(String key);

    // hash (hashmap)
    void hSet(String key, String field, String value);

    // normal
    boolean hasKey(String key);

    // normal
    Set<String> keys(String keyPattern);

    // normal
    void putValue(String key, String value);

    // set (hashset)
    Set<String> sMembers(String key);

    // set (hashset)
    void setAdd(String key, String value);

}

