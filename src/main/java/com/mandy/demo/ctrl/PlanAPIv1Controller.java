package com.mandy.demo.ctrl;

import com.mandy.demo.service.JsonService;
import com.mandy.demo.util.Constant;
import com.mandy.demo.util.JsonValidator;
import com.mandy.demo.util.JwtOAuth;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;

//@RequestMapping("/v1")
@RestController
public class PlanAPIv1Controller {

    private static final Logger logger = LoggerFactory.getLogger(PlanAPIv1Controller.class);

    @Autowired(required = false)
    private JsonService jsonService;


    // only for demo, do not public your token!!!!
    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> genToken() throws NoSuchAlgorithmException {
        String token = JwtOAuth.genJwt();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(new JSONObject().put("token", token).toString());
    }

    @PostMapping(value = "/validate")
    public String verifyToken(@RequestHeader HttpHeaders headers) {
        return JwtOAuth.authorizeToken(headers);
    }

    @GetMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> get(@RequestHeader HttpHeaders headers,
                                      @PathVariable String type,
                                      @PathVariable String id) {

        logger.info("GET PLAN: " + type + "_" + id);
        String objKey = Constant.getObjKey(type, id);

        // 404 - NOT_FOUND
        if (!jsonService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(Constant.MESSAGE, objKey + " does not exist.").toString());
        }

        JSONObject jsonObject = jsonService.getPlan(objKey);

        // e-tag
        String etag = jsonService.getEtag(objKey);
        String ifNoneMatch = headers.getFirst(HttpHeaders.IF_NONE_MATCH); // headers-key: case-insensitive

        // no etag = 200
        if (etag == null || ifNoneMatch == null) {
            return ResponseEntity.ok().body(jsonObject.toString());
        }

        // 304 = same etag
        if (ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        // 200 = different etag = show redis etag
        return ResponseEntity.ok().eTag(etag).body(jsonObject.toString());
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestHeader HttpHeaders headers,
                                         @RequestBody(required = false) String planjson) {
        logger.info("POST PLAN: ");

        // 400 - badRequest
        if (planjson == null || planjson.isEmpty()) {
            return ResponseEntity.badRequest().body(new JSONObject().put(Constant.ERROR, "Request body is empty.").toString());
        }

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            JsonValidator.validate(planjsonObj);
        } catch (ValidationException ex) {
            return ResponseEntity.badRequest().body(new JSONObject().put(Constant.ERROR, ex.getMessage()).toString());
        }

        // 409 - CONFLICT, objKey already exists
        String objKey = Constant.getObjKey(planjsonObj);
        if (jsonService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put(Constant.MESSAGE, "objectId is existed.").toString());
        }

        logger.info("CREATING NEW DATA: key - " + objKey);
        String id = planjsonObj.getString(Constant.OBJECT_ID);
        String type = planjsonObj.getString(Constant.OBJECT_TYPE);
        jsonService.savePlan(planjsonObj, type);

        JSONObject jsonObject = jsonService.getPlan(objKey);
        String newEtag = jsonService.newEtag(objKey, jsonObject);

        JSONObject cloneJsonObject = new JSONObject(new JSONTokener(planjson));
        jsonService.sendEachObject(cloneJsonObject, type, id, type, type+"_join", new HashSet<>(), null, null,"SAVE");

        // 201 - created, return newEtag
        return ResponseEntity.created(URI.create("/v1/" + type + "/" + id))
                .eTag(newEtag).body(new JSONObject().put(Constant.OBJECT_ID, id).put(Constant.OBJECT_TYPE, type).toString());
    }

    @DeleteMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> delete(@RequestHeader HttpHeaders headers,
                                         @PathVariable String id) {
        logger.info("DELETE PLAN: plan_" + id);
        String objKey = Constant.getObjKey("plan", id);

        // 404 - objKey NOT_FOUND
        if (!jsonService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(Constant.MESSAGE, "ObjectId does not exist").toString());
        }

        // 412 - PRECONDITION_FAILED = if-match is different
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH); // optional??
        String etag = jsonService.getEtag(objKey);
        if (ifMatch != null && !etag.equals(ifMatch)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(etag)
                    .body(new JSONObject().put(Constant.MESSAGE, "If-Match is different").toString());
        }

        // delete old plan
        jsonService.deletePlan(objKey);
        jsonService.delEtag(objKey);

        // 204 - NO_CONTENT
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(new JSONObject().put(Constant.MESSAGE, "objectId is deleted.").toString());
    }


    @PatchMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> patch(@RequestHeader HttpHeaders headers,
                                        @PathVariable String id,
                                        @RequestBody String planjson) {

        logger.info("PATCH PLAN: plan_" + id);
        String objKey = Constant.getObjKey("plan", id);

        // 404 - objKey NOT_FOUND
        if (!jsonService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(Constant.MESSAGE, "ObjectId does not exist").toString());
        }

        // 428 - PRECONDITION_REQUIRED = no if-match
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        if (ifMatch == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject().put(Constant.MESSAGE, "header does not have If-Match").toString());
        }

        // 412 - PRECONDITION_FAILED = if-match is different
        String etag = jsonService.getEtag(objKey);
        if (!ifMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(etag)
                    .body(new JSONObject().put(Constant.MESSAGE, "If-Match is different").toString());
        }

        // add new subObject of plan
        JSONObject allJsonObj = jsonService.mergeJson(new JSONObject(planjson), objKey);
        objKey = jsonService.updatePlan(allJsonObj, "plan");

        JSONObject jsonObject = jsonService.getPlan(objKey);
        String newEtag = jsonService.newEtag(objKey, jsonObject);

        jsonService.sendEachObject(jsonObject, "plan", id, "plan", "plan"+"_join", new HashSet<>(), null, null, "SAVE");

        // 200 - ok, return newEtag
        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put(Constant.MESSAGE, "Resource updated successfully").toString());
    }

    @PutMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> update(@RequestHeader HttpHeaders headers,
                                         @PathVariable String id,
                                         @RequestBody String planjson) {
        logger.info("PUT PLAN: plan_" + id);
        String objKey = Constant.getObjKey("plan", id);

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            JsonValidator.validate(planjsonObj);
        } catch (ValidationException ex) {
            return ResponseEntity.badRequest().body(new JSONObject().put(Constant.ERROR, ex.getMessage()).toString());
        }

        // 404 - objKey NOT_FOUND
        if (!jsonService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(Constant.MESSAGE, "ObjectId does not exist").toString());
        }

        // 428 - PRECONDITION_REQUIRED = no if-match
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        if (ifMatch == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject().put(Constant.MESSAGE, "header does not have If-Match").toString());
        }

        // 412 - PRECONDITION_FAILED = if-match is different, return actual planEtag
        String etag = jsonService.getEtag(objKey);
        if (!ifMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(etag)
                    .body(new JSONObject().put(Constant.MESSAGE, "If-Match is different").toString());
        }

        // delete old plan
        // create new plan
        objKey = jsonService.updatePlan(new JSONObject(planjson), "plan");

        JSONObject jsonObject = jsonService.getPlan(objKey);
        String newEtag = jsonService.newEtag(objKey, jsonObject);

        jsonService.sendEachObject(jsonObject, "plan", id, "plan", "plan"+"_join", new HashSet<>(), null, null, "SAVE");

        // 200 - ok, return newEtag
        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put(Constant.MESSAGE, "Resource updated successfully").toString());
    }
}
