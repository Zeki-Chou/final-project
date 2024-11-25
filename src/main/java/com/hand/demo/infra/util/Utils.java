package com.hand.demo.infra.util;

import io.choerodon.core.exception.CommonException;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Utils
 */
public class Utils {
    private Utils() {}

    /**
     * make get request to get response entity for Iam response body.
     * throw error if response status not return 200
     * @param iamRemoteService iam remote service
     * @return iam response body
     */
    public static JSONObject getIamJSONObject(IamRemoteService iamRemoteService) {
        ResponseEntity<String> iamResponse = iamRemoteService.selectSelf();

        if (!iamResponse.getStatusCode().equals(HttpStatus.OK)) {
            throw new CommonException("Error getting iam object");
        }

        String responseBody = iamResponse.getBody();
        return new JSONObject(responseBody);
    }
}
