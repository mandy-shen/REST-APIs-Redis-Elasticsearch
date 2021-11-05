package com.monhong.demo.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monhong.demo.service.PlanService;
import com.monhong.demo.util.JsonValidator;
import com.monhong.demo.util.JwtOAuth;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static com.monhong.demo.util.Constant.*;

@RequestMapping("/v1")
@RestController
public class PlanAPIv1Controller {

    private static final Logger logger = LoggerFactory.getLogger(PlanAPIv1Controller.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final PlanService planService;

    public PlanAPIv1Controller(PlanService planService) {
        this.planService = planService;
    }

    // only for demo, do not public your token!!!!
    @GetMapping(value = "/genToken")
    public ResponseEntity<String> getToken() throws NoSuchAlgorithmException {
        String token = JwtOAuth.genJwt();
        return new ResponseEntity<>(token, HttpStatus.CREATED);
    }

    @GetMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> get(@RequestHeader HttpHeaders headers,
                                      @PathVariable String type,
                                      @PathVariable String id) throws JsonProcessingException {

        logger.info("GET PLAN: " + type + "_" + id);
        String objKey = getObjKey(type, id);

        String returnValue = JwtOAuth.authorizeToken(headers);
        if (!"Valid Token".equals(returnValue))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put(ERROR, returnValue).toString());

        // 404 - NOT_FOUND
        if (!planService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, objKey + " does not exist.").toString());
        }

        Map<String, Object> foundValue = planService.getPlan(objKey);

        // e-tag
        String etag = planService.getEtag(objKey, "eTag");
        String ifNoneMatch = headers.getFirst(HttpHeaders.IF_NONE_MATCH); // headers-key: case-insensitive

        // no etag = 200
        if (etag == null || ifNoneMatch == null) {
            return ResponseEntity.ok().body(objectMapper.writeValueAsString(foundValue));
        }

        // 304 = same etag
        if (ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        // 200 = different etag = show redis etag
        return ResponseEntity.ok().eTag(etag).body(objectMapper.writeValueAsString(foundValue));
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestHeader HttpHeaders headers,
                                         @RequestBody(required = false) String planjson) {
        logger.info("POST PLAN: ");

        // 401 - UNAUTHORIZED
        String returnValue = JwtOAuth.authorizeToken(headers);
        if (!"Valid Token".equals(returnValue))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put(ERROR, returnValue).toString());

        // 400 - badRequest
        if (planjson == null || planjson.isEmpty()) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, "Request body is empty.").toString());
        }

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            JsonValidator.validate(planjsonObj);
        } catch (ValidationException ex) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, ex.getMessage()).toString());
        }

        // 409 - CONFLICT, objKey already exists
        String objKey = getObjKey(planjsonObj);
        if (planService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put(MESSAGE, "objectId is existed.").toString());
        }

        logger.info("CREATING NEW DATA: key - " + objKey);
        String id = planjsonObj.getString(OBJECT_ID);
        String type = planjsonObj.getString(OBJECT_TYPE);
        String newEtag = planService.savePlan(objKey, planjsonObj);

        // 201 - created, return newEtag
        return ResponseEntity.created(URI.create("/v1/" + type + "/" + id))
                .eTag(newEtag).body(new JSONObject().put(OBJECT_ID, id).put(OBJECT_TYPE, type).toString());
    }

    @DeleteMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> delete(@RequestHeader HttpHeaders headers,
                                         @PathVariable String id) {
        logger.info("DELETE PLAN: plan_" + id);
        String objKey = getObjKey("plan", id);

        // 401 - UNAUTHORIZED
        String returnValue = JwtOAuth.authorizeToken(headers);
        if (!"Valid Token".equals(returnValue))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put(ERROR, returnValue).toString());

        // 404 - objKey NOT_FOUND
        if (!planService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, "ObjectId does not exist").toString());
        }

        // delete old plan
        planService.deletePlan(objKey);
//        //save plan to MQ
//        messageQueueService.addToMessageQueue(objectId, true);

        return ResponseEntity.ok().body(new JSONObject().put(MESSAGE, "objectId is deleted.").toString());
    }


    @PatchMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> patch(@RequestHeader HttpHeaders headers,
                                        @PathVariable String id,
                                        @RequestBody String planjson) {

        logger.info("PATCH PLAN: plan_" + id);
        String objKey = getObjKey("plan", id);

        // 401 - UNAUTHORIZED
        String returnValue = JwtOAuth.authorizeToken(headers);
        if (!"Valid Token".equals(returnValue))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put(ERROR, returnValue).toString());

        // 428 - PRECONDITION_REQUIRED = no if-match
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        if (ifMatch == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject().put(MESSAGE, "header does not have If-Match").toString());
        }

        // 412 - PRECONDITION_FAILED = if-match is different
        String etag = planService.getEtag(objKey, "eTag");
        if (!ifMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(etag)
                    .body(new JSONObject().put(MESSAGE, "If-Match is different").toString());
        }

        // 404 - objKey NOT_FOUND
        if (!planService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, "ObjectId does not exist").toString());
        }

        // add new subObject of plan
        String newEtag = planService.savePlan(objKey, new JSONObject(planjson));

        // 200 - ok, return newEtag
        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put(MESSAGE, "Resource updated successfully").toString());
    }

    @PutMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> update(@RequestHeader HttpHeaders headers,
                                         @PathVariable String id,
                                         @RequestBody String planjson) {
        logger.info("PUT PLAN: plan_" + id);
        String objKey = getObjKey("plan", id);

        // 401 - UNAUTHORIZED
        String returnValue = JwtOAuth.authorizeToken(headers);
        if (!"Valid Token".equals(returnValue))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put(ERROR, returnValue).toString());

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            JsonValidator.validate(planjsonObj);
        } catch (ValidationException ex) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, ex.getMessage()).toString());
        }

        // 428 - PRECONDITION_REQUIRED = no if-match
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        if (ifMatch == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject().put(MESSAGE, "header does not have If-Match").toString());
        }

        // 412 - PRECONDITION_FAILED = if-match is different, return actual planEtag
        String etag = planService.getEtag(objKey, "eTag");
        if (!ifMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .eTag(etag)
                    .body(new JSONObject().put(MESSAGE, "If-Match is different").toString());
        }

        // 404 - objKey NOT_FOUND
        if (!planService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, "ObjectId does not exist").toString());
        }

        // delete old plan
        planService.deletePlan(objKey);
        // create new plan
        String newEtag = planService.savePlan(objKey, planjsonObj);

        // 200 - ok, return newEtag
        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put(MESSAGE, "Resource updated successfully").toString());
    }
}
