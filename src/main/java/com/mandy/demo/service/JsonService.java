package com.mandy.demo.service;


import com.mandy.demo.config.RabbitConfig;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;


@Service
public class JsonService {

    private static final Logger logger = LoggerFactory.getLogger(JsonService.class);

    public static final String ELASTIC_URL = "http://localhost:9200";

    @Autowired(required = false)
    private JedisPool jedisPool;

    private final RabbitTemplate rabbit;

    public JsonService(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }



    private JedisPool getJedisPool() {
        if (this.jedisPool == null) {
            this.jedisPool = new JedisPool();
        }
        return this.jedisPool;
    }

    public String savePlan(JSONObject json, String objectType) {
        // temp array of keys to remove from json object
        ArrayList<String> keysToDelete = new ArrayList<>();

        // Iterate through the json
        for(String key : json.keySet()) {
            // check if the value of key is JSONObject or JSONArray
            // first get current Value
            Object currentValue = json.get(key);
            if(currentValue instanceof JSONObject) {
                String objectKey = this.savePlan((JSONObject)currentValue, key);
                // remove this value from JSON, as it will be stored separately
                keysToDelete.add(key);

                // save the relation as separate key in redis
                Jedis jedis = this.getJedisPool().getResource();
                String relationKey = objectType + "_" + json.get("objectId") + "_" + key;
                jedis.set(relationKey, objectKey);
                jedis.close();

            } else if (currentValue instanceof JSONArray) {
                JSONArray currentArrayValue = (JSONArray)currentValue;
                //temp array to store keys of individual objects
                String[] tempValues = new String[currentArrayValue.length()];

                //iterate through the array
                for (int i = 0; i < currentArrayValue.length(); i++) {
                    if (currentArrayValue.get(i) instanceof JSONObject) {
                        JSONObject arrayObject = (JSONObject)currentArrayValue.get(i);
                        String arrayObjectKey = this.savePlan(arrayObject, (String)arrayObject.get("objectType"));

                        tempValues[i] = arrayObjectKey;
                    }
                }

                keysToDelete.add(key);

                // save the Array as separate key in redis
                Jedis jedis = this.getJedisPool().getResource();
                String relationKey = objectType + "_" + json.get("objectId") + "_" + key;
                jedis.set(relationKey, Arrays.toString(tempValues));
                jedis.close();

            }
        }

        // Remove objects from json that are stored separately
        for (String key : keysToDelete) {
            json.remove(key);
        }

        //save the current object in redis
        String objectKey = objectType + "_" + json.get("objectId");

        Jedis jedis = this.getJedisPool().getResource();
        jedis.set(objectKey, json.toString());
        jedis.close();

        return objectKey;
    }

    public JSONObject getPlan(String planKey) {
        JedisPool jedisPool = new JedisPool();
        Jedis jedis;
        JSONObject json;
        if (isStringArray(planKey)) {
            ArrayList<JSONObject> arrayValue = getFromArrayString(planKey);
            json = new JSONObject(arrayValue);
        } else {
            jedis = jedisPool.getResource();
            String jsonString = jedis.get(planKey);
            jedis.close();
            if (jsonString == null || jsonString.isEmpty()) {
                return null;
            }
            json = new JSONObject(jsonString);
        }

        // fetch additional relations for the object, if present
        jedis = jedisPool.getResource();
        Set<String> jsonRelatedKeys = jedis.keys(planKey + "_*");
        jedis.close();

        for (String partObjKey : jsonRelatedKeys) {
            String partObjectKey = partObjKey.substring(partObjKey.lastIndexOf('_') + 1);

            // fetch the id stored at partObjKey
            jedis = jedisPool.getResource();
            String partObjectDBKey = jedis.get(partObjKey);
            jedis.close();
            if (partObjectDBKey == null || partObjectDBKey.isEmpty()) {
                continue;
            }

            if (isStringArray(partObjectDBKey)) {
                ArrayList<JSONObject> arrayValue = getFromArrayString(partObjectDBKey);
                json.put(partObjectKey, arrayValue);
            } else {
                JSONObject partObj = getPlan(partObjectDBKey);
                //add partObj to original object
                json.put(partObjectKey, partObj);
            }
        }

        return json;
    }

    public boolean deletePlan(String planKey) {

        JedisPool jedisPool = new JedisPool();
        Jedis jedis;

        if(isStringArray(planKey)) {
            // delete all keys in the array
            String[] arrayKeys = planKey.substring(planKey.indexOf("[")+1, planKey.lastIndexOf("]")).split(", ");
            for (String key : arrayKeys) {
                if(!deletePlan(key)) {
                    //deletion failed
                    return false;
                }
            }
        } else {
            jedis = jedisPool.getResource();
            if(jedis.del(planKey) < 1) {
                // deletion failed
                jedis.close();
                return false;
            }

            String[] split = planKey.split("_");
            Map<String, String> actionMap = new HashMap<>();
            actionMap.put("operation", "DELETE");
            actionMap.put("uri", ELASTIC_URL);
            actionMap.put("index", "plan");
            actionMap.put("body", split[1]);

            logger.info("Sending message: " + actionMap);

            rabbit.convertAndSend(RabbitConfig.MESSAGE_QUEUE, actionMap);

            jedis.close();
        }

        // fetch additional relations for the object, if present
        jedis = jedisPool.getResource();
        Set<String> jsonRelatedKeys = jedis.keys(planKey + "_*");
        jedis.close();

        for (String partObjKey : jsonRelatedKeys) {
            String partObjectKey = partObjKey.substring(partObjKey.lastIndexOf('_') + 1);

            // fetch the id stored at partObjKey
            jedis = jedisPool.getResource();
            String partObjectDBKey = jedis.get(partObjKey);
            if (jedis.del(partObjKey) < 1) {
                //deletion failed
                return false;
            }

            String[] split_2 = partObjKey.split("_");
            Map<String, String> actionMap = new HashMap<>();
            actionMap.put("operation", "DELETE");
            actionMap.put("uri", ELASTIC_URL);
            actionMap.put("index", "plan");
            actionMap.put("body", split_2[1]);
            logger.info("Sending message: " + actionMap);

            rabbit.convertAndSend(RabbitConfig.MESSAGE_QUEUE, actionMap);

            jedis.close();
            if (partObjectDBKey == null || partObjectDBKey.isEmpty()) {
                continue;
            }

            if (isStringArray(partObjectDBKey)) {
                String[] arrayKeys = partObjectDBKey.substring(partObjectDBKey.indexOf("[") + 1, partObjectDBKey.lastIndexOf("]")).split(", ");
                for (String key : arrayKeys) {
                    if (!deletePlan(key)) {
                        return false;
                    }
                }
            } else {
                if (!deletePlan(partObjectDBKey)) {
                    return false;
                }
            }
        }

        return true;
    }

    public String updatePlan(JSONObject json, String objectType) {
        if(!deletePlan(objectType + "_" + json.get("objectId"))) {
            return null;
        }

        return savePlan(json, objectType);
    }

    // merge the incoming json object with the object in db.
    public JSONObject mergeJson(JSONObject json, String objectKey) {
        JSONObject savedObject = this.getPlan(objectKey);
        if (savedObject == null)
            return null;

        // iterate the new json object
        for(String jsonKey : json.keySet()) {
            Object jsonValue = json.get(jsonKey);

            // check if this is an existing object
            if (savedObject.get(jsonKey) == null) {
                savedObject.put(jsonKey, jsonValue);
            } else {
                if (jsonValue instanceof JSONObject) {
                    JSONObject jsonValueObject = (JSONObject)jsonValue;
                    String jsonObjKey = jsonKey + "_" + jsonValueObject.get("objectId");
                    if (((JSONObject)savedObject.get(jsonKey)).get("objectId").equals(jsonValueObject.get("objectId"))) {
                        savedObject.put(jsonKey, jsonValue);
                    } else {
                        JSONObject updatedJsonValue = this.mergeJson(jsonValueObject, jsonObjKey);
                        savedObject.put(jsonKey, updatedJsonValue);
                    }
                } else if (jsonValue instanceof JSONArray) {
                    JSONArray jsonValueArray = (JSONArray) jsonValue;
                    JSONArray savedJSONArray = savedObject.getJSONArray(jsonKey);
                    for (int i = 0; i < jsonValueArray.length(); i++) {
                        JSONObject arrayItem = (JSONObject)jsonValueArray.get(i);
                        //check if objectId already exists in savedJSONArray
                        int index = getIndexOfObjectId(savedJSONArray, (String)arrayItem.get("objectId"));
                        if(index >= 0) {
                            savedJSONArray.remove(index);
                        }
                        savedJSONArray.put(arrayItem);
                    }
                    savedObject.put(jsonKey, savedJSONArray);
                } else {
                    savedObject.put(jsonKey, jsonValue);
                }
            }

        }

        return savedObject;
    }

    private boolean isStringArray(String str) {
        if (str.indexOf('[') < str.indexOf(']')) {
            return str.substring((str.indexOf('[') + 1), str.indexOf(']')).split(", ").length > 0;
        } else {
            return false;
        }
    }

    private ArrayList<JSONObject> getFromArrayString(String keyArray) {
        ArrayList<JSONObject> jsonArray = new ArrayList<>();
        String[] array = keyArray.substring((keyArray.indexOf('[') + 1), keyArray.indexOf(']')).split(", ");

        for (String key : array) {
            JSONObject partObj = getPlan(key);
            jsonArray.add(partObj);
        }

        return jsonArray;
    }

    private int getIndexOfObjectId(JSONArray array, String objectId) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject arrayObj = (JSONObject) array.get(i);
            String itemId = (String) arrayObj.get("objectId");
            if (objectId.equals(itemId)){
                return i;
            }
        }
        return -1;
    }

    public void sendObject(JSONObject jsonObj, String keyName, String parentId) {
        JSONObject thisObjOnly = new JSONObject();
        String thisObjId = jsonObj.getString("objectId");
        String thisObjType = jsonObj.getString("objectType");

        boolean hasChild = false;
        boolean isChild = parentId != null;

        for (String key : jsonObj.keySet()) {
            Object obj = jsonObj.get(key);

            if (obj instanceof JSONObject) {

                hasChild = true;
                sendObject((JSONObject) obj, key, thisObjId);

            } else if (obj instanceof JSONArray) {

                for (Object subObj : (JSONArray) obj) {
                    hasChild = true;
                    sendObject((JSONObject) subObj, key, thisObjId);
                }

            } else {
                thisObjOnly.put(key, obj);
            }
        }

        logger.info("keyName: " + keyName + ", hasChild=" + hasChild + ", isChild=" + isChild);

        // view thisObj as parent
        if (hasChild) {
            JSONObject joinItself = new JSONObject();
            joinItself.put("name", thisObjType);
            thisObjOnly.put("plan_join", joinItself); // related: parent-child in es
        }

        // thisObj is child
        if (isChild) {
            JSONObject joinChild = new JSONObject();
            joinChild.put("name", keyName);
            joinChild.put("parent", parentId);
            thisObjOnly.put("plan_join", joinChild); // related: parent-child in es
        }

        logger.debug(thisObjOnly.toString(6));

        // thisObj save
        Map<String, String> saveMap = new HashMap<>();
        saveMap.put("operation", "SAVE");
        saveMap.put("uri", ELASTIC_URL);
        saveMap.put("index", "plan");
        saveMap.put("body", thisObjOnly.toString());
        saveMap.put("parentId", parentId);

        logger.info("Sending message: " + saveMap);
        rabbit.convertAndSend(RabbitConfig.MESSAGE_QUEUE, saveMap);
    }

    private Jedis cache = new Jedis();

    public String getEtag(String key) {
        String ETagKey = key + "|" + "ETag";
        return cache.get(ETagKey);
    }

    public boolean hasKey(String key) {
        return cache.get(key) != null;
    }

    public String newEtag(String key, JSONObject object) {
        String ETagKey = key + "|" + "ETag";

        String newEtag = DigestUtils.md5Hex(object.toString());
        cache.set(ETagKey, newEtag);
        return newEtag;
    }

    public void delEtag(String key) {
        String ETagKey = key + "|" + "ETag";
        cache.del(ETagKey);
    }
}

