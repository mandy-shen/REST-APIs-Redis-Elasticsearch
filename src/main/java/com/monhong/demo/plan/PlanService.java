package com.monhong.demo.plan;

import com.monhong.demo.validator.Validator;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.monhong.demo.validator.Constant.getObjKey;

@Service
public class PlanService {

    /**
     * Avoid field @Autowired
     * https://stackoverflow.com/questions/39890849/what-exactly-is-field-injection-and-how-to-avoid-it
     */
    private PlanRepository repository;

    public PlanService(PlanRepository repository) {
        this.repository = repository;
    }

    public boolean hasObjKey(String objKey) {
        return repository.hasObjKey(objKey);
    }

    public String getObject(String objKey) {
        if (!hasObjKey(objKey))
            return Strings.EMPTY;

        Map<String, String> map = repository.get(objKey);

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

        return "{" + str + "}";
    }

    public StringBuilder getArray(String[] objKeys) {
        String str = Arrays
                .stream(objKeys)
                .map(this::getObject)
                .collect(Collectors.joining(","));

        return new StringBuilder().append("[").append(str).append("]");
    }

    public void deleteObject(String objKey) {
        if (!hasObjKey(objKey))
            return;

        Map<String, String> map = repository.get(objKey);

        for (String key: map.keySet()) {
            String value = map.get(key);

            if (value.contains(","))
                deleteArray(value.split(","));
            else if (value.contains("_"))
                deleteObject(value);

            repository.delete(objKey, key);
        }
    }

    public void deleteArray(String[] objKeys) {
        for (String objKey: objKeys) {
            deleteObject(objKey);
        }
    }

    public void putObject(JSONObject jsonObject) {
        String objKey = getObjKey(jsonObject);
        Iterator<String> keys = jsonObject.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object obj = jsonObject.get(key);

            if (obj instanceof JSONObject) {
                JSONObject subjObj = (JSONObject) obj;
                repository.put(objKey, key, getObjKey(subjObj));
                putObject(subjObj);
            } else if (obj instanceof JSONArray) {
                JSONArray jArray = (JSONArray) obj;
                List<String> list = new ArrayList<>(jArray.length());

                for (int i = 0 ; i < jArray.length(); i++) {
                    JSONObject subjObj = jArray.getJSONObject(i);
                    repository.put(objKey, key, getObjKey(subjObj));
                    putObject(subjObj);
                    list.add(getObjKey(subjObj));
                }

                repository.put(objKey, key, String.join(",", list));
            } else {
                repository.put(objKey, key, obj.toString());
            }
        }
    }

}
