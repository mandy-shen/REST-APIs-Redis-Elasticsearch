package com.monhong.demo.validator;

import org.json.JSONObject;

public class Constant {

    public static String OBJECT_ID = "objectId";
    public static String OBJECT_TYPE = "objectType";

    public static String getObjKey(String type, String id) {
        return type + "_" + id;
    }

    public static String getObjKey(JSONObject jsonObject) {
        String type = jsonObject.getString(OBJECT_TYPE);
        String id = jsonObject.getString(OBJECT_ID);
        return type + "_" + id;
    }

    public static String MESSAGE = "Message";
    public static String ERROR = "Error";

}
