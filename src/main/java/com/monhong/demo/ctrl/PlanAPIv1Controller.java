package com.monhong.demo.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monhong.demo.service.PlanService;
import com.monhong.demo.validator.Validator;
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
import java.util.Map;

import static com.monhong.demo.validator.Constant.*;

@RequestMapping("/v1")
@RestController
public class PlanAPIv1Controller {

    private static final Logger logger = LoggerFactory.getLogger(PlanAPIv1Controller.class);
    private static ObjectMapper objectMapper = new ObjectMapper();

    private PlanService planService;
    public PlanAPIv1Controller(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> get(@RequestHeader HttpHeaders headers,
                                      @PathVariable String type,
                                      @PathVariable String id) throws JsonProcessingException {

        logger.info("get DATA: type - " + type + "; id - " + id);
        String objKey = getObjKey(type, id);

        // authorize
//        logger.info("AUTHORIZATION: GOOGLE_ID_TOKEN: " + idToken);
//
//        if (!authorizationService.authorize(idToken.substring(7))) {
//            logger.error("TOKEN AUTHORIZATION - google token expired");
//
//            String message = MessageUtil.build(MessageEnum.AUTHORIZATION_ERROR);
//            return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
//        }
//        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        // 404 - NOT_FOUND
        if (!planService.hasKey(objKey)) {
            logger.info("OBJECT NOT FOUND - " + objKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, objKey + " does not exist.").toString());
        }

        Map<String, Object> foundValue = planService.getPlan(objKey);

        // e-tag
        String etag = planService.getEtag(objKey, "eTag");
        String headerEtag = headers.getFirst(HttpHeaders.IF_NONE_MATCH); // headers-key: case-insensitive

        // no etag = 200
        if (etag == null || headerEtag == null) {
            return ResponseEntity.ok().body(objectMapper.writeValueAsString(foundValue));
        }

        // 304 = same etag
        if (headerEtag.equals(etag)) {
            logger.info("CACHING AVAILABLE: " + objKey);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        // 200 = different etag = show redis etag
        return ResponseEntity.ok().eTag(etag).body(objectMapper.writeValueAsString(foundValue));
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(//@RequestHeader("Authorization") String idToken,
                                         @RequestBody(required = false) String planjson) {
//        logger.info("GOOGLE-ID_TOKEN:" + idToken);
        // authorize
//        if (!authorizationService.authorize(idToken.substring(7))) {
//            logger.error("TOKEN AUTHORIZATION - google token expired");
//
//            String message = MessageUtil.build(MessageEnum.AUTHORIZATION_ERROR);
//            return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
//        }
//        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");


        // 400 - badRequest
        if (planjson == null || planjson.isEmpty()) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, "Request body is empty.").toString());
        }

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            Validator.validate(planjsonObj);
        } catch (ValidationException ex) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + ex.getMessage());
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, ex.getMessage()).toString());
        }

        // 409 - CONFLICT
        if (planService.hasKey(getObjKey(planjsonObj))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put(MESSAGE, "objectId is existed.").toString());
        }

        logger.info("CREATING NEW DATA: key - " + getObjKey(planjsonObj));
        String id = planjsonObj.getString(OBJECT_ID);
        String type = planjsonObj.getString(OBJECT_TYPE);
        String newEtag = planService.savePlan(getObjKey(planjsonObj), planjsonObj);

        // 201 - created
        return ResponseEntity.created(URI.create("/v1/" + type + "/" + id))
                .eTag(newEtag).body(new JSONObject().put(OBJECT_ID, id).put(OBJECT_TYPE, type).toString());
    }

    @DeleteMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> delete(@PathVariable String type, @PathVariable String id) {
        logger.info("DELETE type: " + type + ", id - " + id);
        String objKey = getObjKey(type, id);

        // authorize
//        logger.info("AUTHORIZATION: GOOGLE_ID_TOKEN: " + idToken);
//
//        if (!authorizationService.authorize(idToken.substring(7))) {
//            logger.error("TOKEN AUTHORIZATION - google token expired");
//
//            String message = MessageUtil.build(MessageEnum.AUTHORIZATION_ERROR);
//            return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
//        }
//        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        if (!planService.hasKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, "objectId does not exist.").toString());
        }

        planService.deletePlan(objKey);
        logger.info("DELETED SUCCESSFULLY: " + objKey);

        return ResponseEntity.ok().body(new JSONObject().put(MESSAGE, "objectId is deleted.").toString());
    }


    @PatchMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> patch(//@RequestHeader(value = "authorization", required = false) String idToken,
                                            @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                            @PathVariable String type,
                                            @PathVariable String id,
                                            @RequestBody(required = false) String planjson) {

        logger.info("PATCH PLAN: " + type + ":" + id);
        String objKey = getObjKey(type, id);

        // check authorization
//        if (!authorizationService.authorize(idToken.substring(7))) {
//            logger.error("TOKEN AUTHORIZATION - google token expired");
//
//            String message = MessageUtil.build(MessageEnum.AUTHORIZATION_ERROR);
//            return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
//        }
//        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        // 400 - badRequest
        if (planjson == null || planjson.isEmpty()) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, "Request body is empty.").toString());
        }

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            Validator.validate(planjsonObj);
        } catch (ValidationException ex) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + ex.getMessage());
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, ex.getMessage()).toString());
        }

        // etag check
        String planEtag = planService.getEtag(objKey, "eTag");

        if (ifMatch == null) {
            logger.info("HEADER DOES NOT HAVE IF_MATCH");
            return ResponseEntity
                    .status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject().put(MESSAGE, "header If_match missing").toString());
        }

        if (!ifMatch.equals(planEtag)) {
            logger.info("CONFLICT");
            return ResponseEntity
                    .status(HttpStatus.PRECONDITION_FAILED)
                    .body(new JSONObject().put(MESSAGE, "header If_match does not match").toString());
        }

        // check plan exist
        if (!planService.hasKey(objKey)) {
            logger.info(objKey + " does not exist");
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, objKey + " does not exist").toString());
        }

        // delete old plan
        planService.deletePlan(objKey);

        // update plan
        planService.savePlan(objKey, new JSONObject(planjson));
        logger.info(objKey + " updates successfully");

        return ResponseEntity
                .ok()
                .eTag(planEtag)
                .body(new JSONObject().put(MESSAGE, objKey + " updates successfully").toString());
    }

    @PutMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> update(//@RequestHeader(value = "authorization", required = false) String idToken,
                                             @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                             @PathVariable String type,
                                             @PathVariable String id,
                                             @RequestBody(required = false) String planjson) {
        logger.info("PUT PLAN: " + type + ":" + id);
        String objKey = getObjKey(type, id);

        // check authorization
//        if (!authorizationService.authorize(idToken.substring(7))) {
//            logger.error("TOKEN AUTHORIZATION - google token expired");
//
//            String message = MessageUtil.build(MessageEnum.AUTHORIZATION_ERROR);
//            return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
//        }
//        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        // 400 - badRequest
        if (planjson == null || planjson.isEmpty()) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, "Request body is empty.").toString());
        }

        // 400 - validate error badRequest
        JSONObject planjsonObj = new JSONObject(planjson);
        try {
            Validator.validate(planjsonObj);
        } catch (ValidationException ex) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + ex.getMessage());
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, ex.getMessage()).toString());
        }

        // etag check
        String planEtag = planService.getEtag(objKey, "eTag");

        if (ifMatch == null) {
            logger.info("HEADER DOES NOT HAVE IF_MATCH");
            return ResponseEntity
                    .status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject().put(MESSAGE, "header If_match missing").toString());
        }

        if (!ifMatch.equals(planEtag)) {
            logger.info("CONFLICT");
            return ResponseEntity
                    .status(HttpStatus.PRECONDITION_FAILED)
                    .body(new JSONObject().put(MESSAGE, "header If_match does not match").toString());
        }

        // check plan exist
        if (!planService.hasKey(objKey)) {
            logger.info(objKey + " does not exist");
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, objKey + " does not exist").toString());
        }

        // delete old plan
        planService.deletePlan(objKey);

        // update plan
        planService.savePlan(objKey, new JSONObject(planjson));
        logger.info(objKey + " updates successfully");

        return ResponseEntity
                .ok()
                .eTag(planEtag)
                .body(new JSONObject().put(MESSAGE, objKey + " updates successfully").toString());
    }
}
