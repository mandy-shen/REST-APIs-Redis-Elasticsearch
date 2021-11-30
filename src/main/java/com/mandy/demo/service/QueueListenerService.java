package com.mandy.demo.service;


import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

@Component
public class QueueListenerService {

    private static final Logger logger = LoggerFactory.getLogger(QueueListenerService.class);

    public void receiveMessage(Map<String, String> message) {
        System.out.println(" ------------------------ Message received START ------------------------");
        logger.info("Message received: " + message);

        String operation = message.get("operation");
        String uri = message.get("uri");
        String body = message.get("body");
        String indexName = message.getOrDefault("index", "plan");
        String mainObjectId = message.getOrDefault("mainObjectId", "1");

        switch (operation) {
            case "SAVE": {
                JSONObject jsonBody = new JSONObject(body);
                String type = jsonBody.getString("objectType");
                String id = jsonBody.getString("objectId");

                logger.info("SAVE OPERATION CALLED FOR " + type + " : " + id);
                putObject(uri, indexName, jsonBody, mainObjectId);
                break;
            }
            case "DELETE": {
                logger.info("DELETE OPERATION CALLED FOR " + body);
                deleteIndex(uri, indexName, body);
                break;
            }
        }
        System.out.println(" ------------------------ Message received END ------------------------");
    }

    private int executeRequest(HttpUriRequest request) {
        int statusCode = 0;
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {

            statusCode = response.getStatusLine().getStatusCode();
            if(statusCode > 299){
                logger.info("ElasticSearch getStatusLine: " + response.getStatusLine().toString());
            }
            logger.info("ElasticSearch getEntity: " + EntityUtils.toString(response.getEntity()));

        } catch (IOException e) {
            e.printStackTrace();
        }
        return statusCode;
    }

    private void putObject(String uri, String indexName, JSONObject objectBody, String mainObjectId) {
        String url = uri + "/" + indexName + "/_doc/" + objectBody.getString("objectId") + "?routing=" + mainObjectId;

        logger.info("HttpPut=" + url);
        HttpPut request = new HttpPut(url);

        try {
            request.addHeader(HttpHeaders.CONTENT_TYPE, String.valueOf(ContentType.APPLICATION_JSON));
            request.setEntity(new StringEntity(objectBody.toString()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        executeRequest(request);
    }

    private void deleteIndex(String uri, String indexName, String objectId) {
        String url = uri + "/" + indexName + "/_doc/" + objectId;

        logger.info("HttpDelete=" + url);
        HttpDelete request = new HttpDelete(url);

        executeRequest(request);
    }
}

