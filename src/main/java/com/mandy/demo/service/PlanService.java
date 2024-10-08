package com.mandy.demo.service;

import com.mandy.demo.dao.RedisDao;
import com.mandy.demo.util.Constant;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.*;

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

        // get all attributes
        Iterator<String> keys = jsonObject.keySet().iterator();

        // traverse all attributes for store
        while (keys.hasNext()) {

            String objectKey = Constant.getObjKey(jsonObject);

            String attName = keys.next();
            Object attValue = jsonObject.get(attName);

            // type - Object
            if (attValue instanceof JSONObject) {

                attValue = traverseNode((JSONObject) attValue);

                Map<String, Map<String, Object>> ObjValueMap = (HashMap<String, Map<String, Object>>) attValue;

                String transitiveKey = objectKey + "_" + attName;
                redisDao.setAdd(transitiveKey, ObjValueMap.entrySet().iterator().next().getKey());
            } else if (attValue instanceof JSONArray) {

                // type - Array
                attValue = getNodeList((JSONArray)attValue);

                List<HashMap<String, HashMap<String, Object>>> formatList = (List<HashMap<String, HashMap<String, Object>>>) attValue;
                formatList.forEach((listObject) -> {

                    listObject.entrySet().forEach((listEntry) -> {

                        String internalKey = objectKey + "_" + attName;

                        redisDao.setAdd(internalKey, listEntry.getKey());

                    });

                });
            } else {
                // type - Object
                redisDao.hSet(objectKey, attName, attValue.toString());

                valueMap.put(attName, attValue);
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
                Iterator<Object> jsonIterator = jsonArray.iterator();

                while (jsonIterator.hasNext()) {
                    JSONObject embdObject = (JSONObject) jsonIterator.next();
                    embdObject.put("parent_id", uuid);
                    System.out.println(embdObject.toString());

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
                e = traverseNode((JSONObject )e);
            } else if (e instanceof JSONArray) {
                e = getNodeList((JSONArray)e);
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

    public void delEtag(String key, String etag) {
        redisDao.hDelete(key, etag);
    }

    // populate plan nested node
    public Map<String, Object> populate(String objectKey, Map<String, Object> map, boolean delete) {

        // get all attributes
        Set<String> keys = redisDao.keys(objectKey + "*");

        keys.forEach((key) -> {
            if (key.length() > objectKey.length() && !key.substring(objectKey.length()).contains(SPLITTER_UNDER_SLASH)) {
                return;
            }

            // process key : value
            if (key.equals(objectKey)) {

                if (delete) {
                    redisDao.deleteKey(key);
                } else {

                    // store the string object: key-pair
                    Map<Object, Object> objMap = redisDao.hGetAll(key);

                    objMap.entrySet().forEach((att) -> {

                        String attKey = (String) att.getKey();

                        if (!attKey.equalsIgnoreCase("eTag")) {
                            String attValue = att.getValue().toString();
                            map.put(attKey, isNumberValue(attValue)? Integer.parseInt(attValue) : att.getValue());
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
                        if (delete) {
                            populate(member, null, delete);
                        } else {
                            Map<String, Object> listMap = new HashMap<>();
                            objectList.add(populate(member, listMap, delete));
                        }
                    });

                    if (delete) {
                        redisDao.deleteKey(key);
                    } else {
                        map.put(subKey, objectList);
                    }
                } else {
                    // process nested object

                    if (delete) {
                        redisDao.deleteKeys(Arrays.asList(key, objSet.iterator().next()));
                    } else {

                        Map<Object, Object> values = redisDao.hGetAll(objSet.iterator().next());
                        Map<String, Object> objMap = new HashMap<>();

                        values.entrySet().forEach((value) -> {

                            String name = value.getKey().toString();
                            String val = value.getValue().toString();

                            objMap.put(name, isNumberValue(val)? Integer.parseInt(val) : value.getValue());

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
        } catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }
}
