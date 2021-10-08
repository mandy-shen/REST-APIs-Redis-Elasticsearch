package com.monhong.demo.plan;

import com.monhong.demo.validator.Validator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private RedisTemplate<String, String> redisTemplate;
    private HashOperations hashOperations;

    public PlanService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    public boolean isEmpty(String objKey) {
        return hashOperations.entries(objKey).isEmpty();
    }

    public String getObject(String objKey) {
        if (isEmpty(objKey))
            return "";

        Map<String, String> map = hashOperations.entries(objKey);

        String str = map.entrySet()
                .stream()
                .map(entry -> {
                    String key = "\"" + entry.getKey()+ "\":";
                    String value = entry.getValue();

                    if (value.contains(","))
                        return key + getArray(value.split(",")).toString();
                    else if (value.contains("_"))
                        return key + getObject(value);
                    else if (Validator.isNumeric(value))
                        return key + value;
                    else
                        return key + "\"" + value + "\"";

                })
                .collect(Collectors.joining(","));

        return new StringBuilder().append("{").append(str).append("}").toString();
    }

    public StringBuilder getArray(String[] objKeys) {
        String str = Arrays
                .stream(objKeys)
                .map(k -> getObject(k))
                .collect(Collectors.joining(","));

        return new StringBuilder().append("[").append(str).append("]");
    }

    public void deleteObject(String objKey) {
        if (isEmpty(objKey))
            return;

        Map<String, String> map = hashOperations.entries(objKey);

        for (String key: map.keySet()) {
            String value = map.get(key);

            if (value.contains(","))
                deleteArray(value.split(","));
            else if (value.contains("_"))
                deleteObject(value);

            hashOperations.delete(objKey, key);
        }
    }

    public void deleteArray(String[] array) {
        for (String key: array) {
            deleteObject(key);
        }
    }

    public boolean isEmpty(JSONObject jsonObject) {
        return isEmpty(getKey(jsonObject));
    }

    public void putObject(JSONObject jsonObject) {
        String objKey = getKey(jsonObject);
        Iterator<String> keys = jsonObject.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object obj = jsonObject.get(key);

            if (obj instanceof JSONObject) {
                JSONObject subjObj = (JSONObject) obj;
                put(objKey, key, getKey(subjObj));
                putObject(subjObj);
            } else if (obj instanceof JSONArray) {
                JSONArray jArray = (JSONArray) obj;
                List<String> list = new ArrayList<>(jArray.length());

                for (int i = 0 ; i < jArray.length(); i++) {
                    JSONObject subjObj = jArray.getJSONObject(i);
                    put(objKey, key, getKey(subjObj));
                    putObject(subjObj);
                    list.add(getKey(subjObj));
                }

                put(objKey, key, list.stream().collect(Collectors.joining(",")));
            } else {
                put(objKey, key, obj.toString());
            }
        }
    }

    public boolean hasKey(String objKey, String key) {
        return hashOperations.hasKey(objKey, key);
    }

    public String get(String objKey, String key) {
        if (!hasKey(objKey, key))
            return "";

        return hashOperations.get(objKey, key).toString();
    }

    public void put(String objKey, String key, String val) {
        hashOperations.put(objKey, key, val);
    }

    public void delete(String objKey, String key) {
        if (!hasKey(objKey, key))
            return;

        hashOperations.delete(objKey, key);
    }

    private String getKey(JSONObject jsonObject) {
        String type = jsonObject.getString("objectType");
        String id = jsonObject.getString("objectId");
        return type + "_" + id;
    }
}
