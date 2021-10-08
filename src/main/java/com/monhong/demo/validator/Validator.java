package com.monhong.demo.validator;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

public class Validator {

    private static JSONObject SCHEMA;

    private static JSONObject getScheme() {
        if (SCHEMA != null)
            return SCHEMA;

        try (InputStream ins = new ClassPathResource("./static/scheme.json").getInputStream()) {
            SCHEMA = new JSONObject(new JSONTokener(ins));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return SCHEMA;
    }

    public static void validate(JSONObject jsonObject) {
        JSONObject jsonSchema = getScheme();
        Schema schema = SchemaLoader.load(jsonSchema);
        schema.validate(jsonObject);
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null)
            return false;

        try {
            Integer.parseInt(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }
}
