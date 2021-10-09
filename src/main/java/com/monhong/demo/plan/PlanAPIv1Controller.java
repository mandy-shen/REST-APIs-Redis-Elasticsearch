package com.monhong.demo.plan;

import com.monhong.demo.validator.Validator;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.monhong.demo.validator.Constant.*;

@RequestMapping("/v1")
@RestController
public class PlanAPIv1Controller {

    private PlanService service;
    private PlanEtagService etagService;

    public PlanAPIv1Controller(PlanService service, PlanEtagService etagService) {
        this.service = service;
        this.etagService = etagService;
    }

    @GetMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> getObjectByTypeAndId(@RequestHeader HttpHeaders headers,
                                                       @PathVariable String type, @PathVariable String id) {

        String objKey = getObjKey(type, id);

        // 404 - NOT_FOUND
        if (!service.hasObjKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, "objectId does not exist.").toString());
        }

        if ("plan".equals(type)) {
            String headerEtag = headers.getFirst(HttpHeaders.IF_NONE_MATCH); // headers-key: case-insensitive
            String etag = etagService.getEtag(id);

            if (etag.equals(headerEtag)) {
                // 304 = same etag
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
            } else {
                // 200 = different etag = show redis etag
                return ResponseEntity.ok().eTag(etag).body(service.getObject(objKey));
            }
        }

        return ResponseEntity.ok().body(service.getObject(objKey));
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> createPlan(@RequestBody(required = false) String planObj) {
        // 400 - badRequest
        if (planObj == null || planObj.isEmpty()) {
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, "Request body is empty.").toString());
        }

        JSONObject jsonObject = new JSONObject(planObj);
        try {
            Validator.validate(jsonObject);
        } catch (ValidationException ex) {
            // 400 - badRequest
            return ResponseEntity.badRequest().body(new JSONObject().put(ERROR, ex.getMessage()).toString());
        }

        // 409 - CONFLICT
        if (service.hasObjKey(getObjKey(jsonObject))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put(MESSAGE, "objectId is existed.").toString());
        }

        service.putObject(jsonObject);
        String id = jsonObject.getString(OBJECT_ID);
        String type = jsonObject.getString(OBJECT_TYPE);
        String newEtag = etagService.createEtag(id);

        // 201 - created
        return ResponseEntity.created(URI.create("/v1/" + type + "/" + id))
                .eTag(newEtag).body(new JSONObject().put(OBJECT_ID, id).put(OBJECT_TYPE, type).toString());
    }


    @DeleteMapping(value = "/{type}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> deleteObjectByTypeAndId(@PathVariable String type, @PathVariable String id) {

        String objKey = getObjKey(type, id);

        if (!service.hasObjKey(objKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put(MESSAGE, "objectId does not exist.").toString());
        }

        service.deleteObject(objKey);

        if (etagService.hasEtag(id))
            etagService.deleteEtag(id);

        return ResponseEntity.ok().body(new JSONObject().put(MESSAGE, "objectId is deleted.").toString());
    }
}
