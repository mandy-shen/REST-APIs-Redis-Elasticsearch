package com.monhong.demo.service;

import com.monhong.demo.dao.RedisDao;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.monhong.demo.util.Constant.getObjKey;

@Service
public class PlanService {

    private final static String SPLITTER_UNDER_SLASH = "_";
    private final Map<String,String> relationMap = new HashMap<>();
    private final RedisDao redisDao;

    /**
     * Avoid field @Autowired
     * https://stackoverflow.com/questions/39890849/what-exactly-is-field-injection-and-how-to-avoid-it
     */
    public PlanService(RedisDao redisDao) {
        this.redisDao = redisDao;
    }

    public boolean hasKey(String key) {
        return redisDao.hasKey(key);
    }

    public void deletePlan(String key) {
        populate(key, null, true);
    }

    public String savePlan(String key, JSONObject object) {
        Map<String, Object> objectMap = nestStore(key, object);
        indexQueue(object, object.getString("objectId"));

        String newEtag = DigestUtils.md5Hex(object.toString());
        redisDao.hSet(key, "eTag", newEtag);
        return newEtag;
    }

    private Map<String, Object> nestStore(String key, JSONObject object) {
        traverseNode(object);

        Map<String, Object> output = new HashMap<>();
        populate(key, output, false);
        return output;
    }

    // store the nested json object
    public Map<String, Map<String, Object>> traverseNode(JSONObject jsonObject) {
        Map<String, Map<String, Object>> objMap = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();

        // traverse all attributes for store
        for (String key : jsonObject.keySet()) {
            String objectKey = getObjKey(jsonObject);
            Object obj = jsonObject.get(key);

            if (obj instanceof JSONObject) {
                // type - Object
                Map<String, Map<String, Object>> ObjValueMap = traverseNode((JSONObject) obj);
                String transitiveKey = objectKey + "_" + key;
                redisDao.setAdd(transitiveKey, ObjValueMap.entrySet().iterator().next().getKey());
            } else if (obj instanceof JSONArray) {
                // type - Array
                List<HashMap<String, HashMap<String, Object>>> formatList = (List<HashMap<String, HashMap<String, Object>>>) obj;
                formatList.forEach((listObject) -> {
                    listObject.forEach((key1, value) -> {
                        String internalKey = objectKey + "_" + key;
                        redisDao.setAdd(internalKey, key1);
                    });
                });
            } else {
                // type - Object
                redisDao.hSet(objectKey, key, obj.toString());
                valueMap.put(key, obj);
                objMap.put(objectKey, valueMap);
            }

        }

        return objMap;
    }

    private void indexQueue(JSONObject jsonObject, String uuid) {

        Map<String, String> simpleMap = new HashMap<>();

        for (Object key : jsonObject.keySet()) {
            String attributeKey = String.valueOf(key);
            Object attributeVal = jsonObject.get(String.valueOf(key));
            String edge = attributeKey;

            if (attributeVal instanceof JSONObject) {
                JSONObject embdObject = (JSONObject) attributeVal;

                JSONObject joinObj = new JSONObject();
                if (edge.equals("planserviceCostShares") && embdObject.getString("objectType").equals("membercostshare")) {
                    joinObj.put("name", "planservice_membercostshare");
                } else {
                    joinObj.put("name", embdObject.getString("objectType"));
                }

                joinObj.put("parent", uuid);
                embdObject.put("plan_service", joinObj);
                embdObject.put("parent_id", uuid);

//                messageQueueService.addToMessageQueue(embdObject.toString(), false);
//                kafkaPub.publish(Constant.ES_POST, embdObject.toString());

            } else if (attributeVal instanceof JSONArray) {

                JSONArray jsonArray = (JSONArray) attributeVal;

                for (Object obj : jsonArray) {
                    JSONObject embdObject = (JSONObject) obj;
                    embdObject.put("parent_id", uuid);
                    System.out.println(embdObject);

                    String embd_uuid = embdObject.getString("objectId");
                    relationMap.put(embd_uuid, uuid);

                    indexQueue(embdObject, embd_uuid);
                }

            } else {
                simpleMap.put(attributeKey, String.valueOf(attributeVal));
            }
        }

        JSONObject joinObj = new JSONObject();
        joinObj.put("name", simpleMap.get("objectType"));

        if (!simpleMap.containsKey("planType")) {
            joinObj.put("parent", relationMap.get(uuid));
        }

        JSONObject obj1 = new JSONObject(simpleMap);
        obj1.put("plan_service", joinObj);
        obj1.put("parent_id", relationMap.get(uuid));

//        messageQueueService.addToMessageQueue(obj1.toString(), false);
//        kafkaPub.publish(Constant.ES_POST, obj1.toString());

    }


    private List<Object> getNodeList(JSONArray attValue) {
        List<Object> list = new ArrayList<>();

        if (attValue == null)
            return list;

        attValue.forEach((e) -> {
            if (e instanceof JSONObject) {
                e = traverseNode((JSONObject) e);
            } else if (e instanceof JSONArray) {
                e = getNodeList((JSONArray) e);
            }
            list.add(e);
        });

        return list;
    }

    public Map<String, Object> getPlan(String key) {
        Map<String, Object> output = new HashMap<>();
        populate(key, output, false);
        return output;
    }

    public String getEtag(String key, String etag) {
        return redisDao.hGet(key, etag);
    }

    // populate plan nested node
    public Map<String, Object> populate(String objectKey, Map<String, Object> map, boolean isDelete) {

        // get all attributes
        Set<String> keys = redisDao.keys(objectKey + "*");

        keys.forEach((key) -> {
            if (key.length() > objectKey.length() && !key.substring(objectKey.length()).contains(SPLITTER_UNDER_SLASH)) {
                return;
            }

            // process key : value
            if (key.equals(objectKey)) {

                if (isDelete) {
                    redisDao.deleteKey(key);
                } else {

                    // store the string object: key-pair
                    Map<Object, Object> objMap = redisDao.hGetAll(key);

                    objMap.forEach((key1, value) -> {
                        String attKey = (String) key1;

                        if (!attKey.equalsIgnoreCase("eTag")) {
                            String attValue = value.toString();
                            map.put(attKey, isNumberValue(attValue) ? Integer.parseInt(attValue) : value);
                        }
                    });
                }
            } else {

                // nest nodes
                String subKey = key.substring((objectKey + SPLITTER_UNDER_SLASH).length());

                Set<String> objSet = redisDao.sMembers(key);

                if (objSet.size() > 1) {
                    // process nested object list
                    List<Object> objectList = new ArrayList<>();

                    objSet.forEach((member) -> {
                        if (isDelete) {
                            populate(member, null, isDelete);
                        } else {
                            Map<String, Object> listMap = new HashMap<>();
                            objectList.add(populate(member, listMap, isDelete));
                        }
                    });

                    if (isDelete) {
                        redisDao.deleteKey(key);
                    } else {
                        map.put(subKey, objectList);
                    }
                } else {
                    // process nested object
                    if (isDelete) {
                        redisDao.deleteKeys(Arrays.asList(key, objSet.iterator().next()));
                    } else {

                        Map<Object, Object> values = redisDao.hGetAll(objSet.iterator().next());
                        Map<String, Object> objMap = new HashMap<>();

                        values.forEach((key1, value1) -> {
                            String name = key1.toString();
                            String val = value1.toString();

                            objMap.put(name, isNumberValue(val) ? Integer.parseInt(val) : value1);
                        });

                        map.put(subKey, objMap);
                    }
                }
            }
        });

        return map;
    }

    private boolean isNumberValue(String strNum) {
        if (strNum == null || strNum.isBlank())
            return false;

        try {
            Integer.parseInt(strNum);
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }
}
