package com.mandy.demo.util;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

public class JsonValidator {

    private static JSONObject SCHEMA;

    private static void initSchema() {
        try (InputStream ins = new ClassPathResource("./static/schema.json").getInputStream()) {
            SCHEMA = new JSONObject(new JSONTokener(ins));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void validate(JSONObject jsonObject) {
        if (SCHEMA == null)
            initSchema();

        Schema schema = SchemaLoader.load(SCHEMA);
        schema.validate(jsonObject);
    }
}
