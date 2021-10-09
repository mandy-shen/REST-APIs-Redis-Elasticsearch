package com.monhong.demo.plan;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import static org.springframework.http.HttpHeaders.ETAG;

@Service
public class PlanEtagService {

    private PlanRepository repository;

    public PlanEtagService(PlanRepository repository) {
        this.repository = repository;
    }

    public boolean hasEtag(String objId) {
        return repository.hasKey(objId, ETAG);
    }

    public String getEtag(String objId) {
        return repository.get(objId, ETAG);
    }

    public String createEtag(String objId) {
        String etag = DigestUtils.md5Hex(objId);
        repository.put(objId, ETAG, etag);
        return etag;
    }

    public void deleteEtag(String objId) {
        repository.delete(objId, ETAG);
    }
}
