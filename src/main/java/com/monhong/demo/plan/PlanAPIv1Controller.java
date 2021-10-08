package com.monhong.demo.plan;

import com.monhong.demo.validator.Validator;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RequestMapping("/v1")
@RestController
public class PlanAPIv1Controller {

    @Autowired
    private PlanEtagService etagService;

    @Autowired
    private PlanService service;

    @GetMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> getObjectByTypeAndId(@RequestHeader HttpHeaders headers,
                                              @PathVariable String type, @PathVariable String id) {

        String objKey = type + "_" + id;

        if (service.isEmpty(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "objectId does not exist.").toString());
        }

        if ("plan".equals(type)) {
            String headerEtag = headers.getFirst("if-none-match");
            String etag = etagService.getEtag(id);

            if (etag.equals(headerEtag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build(); // same = 304
            } else {
                return ResponseEntity.ok().eTag(etag).body(service.getObject(objKey)); // different = show redis etag
            }
        }

        return ResponseEntity.ok().body(service.getObject(objKey));
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> createPlan(@RequestHeader HttpHeaders headers,
                                             @RequestBody(required = false) String planObj) {
        if (planObj == null || planObj.isEmpty())
            return ResponseEntity.badRequest().body(new JSONObject().put("Error", "Request body is empty.").toString());

        JSONObject jsonObject = new JSONObject(planObj);

        try {
            Validator.validate(jsonObject);
        } catch (ValidationException ex) {
            return ResponseEntity.badRequest().body(new JSONObject().put("Error", ex.getMessage()).toString());
        }

        if (!service.isEmpty(jsonObject)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new JSONObject().put("message", "objectId is existed.").toString());
        }

        service.putObject(jsonObject);
        String etag = etagService.createEtag(jsonObject);

        String id = jsonObject.getString("objectId");
        String type = jsonObject.getString("objectType");

        String res = "{objectId: " + id + ", objectType: " + type  + "}";

        return ResponseEntity.created(URI.create("/v1/" + type + "/" + id)).eTag(etag).body(new JSONObject(res).toString());
    }


    @DeleteMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> deleteObjectByTypeAndId(@RequestHeader HttpHeaders headers,
                                                 @PathVariable String type, @PathVariable String id) {
        String objKey = type + "_" + id;

        if (service.isEmpty(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Not Found. objectId is deleted.").toString());
        }

        service.deleteObject(objKey);

        if (etagService.hasEtag(id))
            etagService.deleteEtag(id);

        return ResponseEntity.ok()
                .body(new JSONObject().put("message", "OK. objectId is deleted.").toString());
    }
}
