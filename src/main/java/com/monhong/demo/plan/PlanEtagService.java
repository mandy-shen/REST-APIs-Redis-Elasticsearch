package com.monhong.demo.plan;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlanEtagService {

    private static String ETAG = "etag";

    @Autowired
    private PlanService service;

    public boolean hasEtag(String objId) {
        return service.hasKey(objId, ETAG);
    }

    public String getEtag(String objId) {
        return service.get(objId, ETAG);
    }

    public String createEtag(JSONObject jsonObject) {
        String etag = DigestUtils.md5Hex(jsonObject.toString());
        service.put(jsonObject.getString("objectId"), ETAG, etag);
        return etag;
    }

    public void deleteEtag(String objId) {
        service.delete(objId, ETAG);
    }
}
