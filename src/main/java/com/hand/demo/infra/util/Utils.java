package com.hand.demo.infra.util;

import io.choerodon.core.exception.CommonException;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * convert string of ids to list of long id
     * @param ids string of ids e.g: 1,2,3
     * @return list of split ids e.g: [1,2,3]
     */
    public static List<Long> convertStringIdstoList(String ids) {
        return Stream.of(ids.split(",")).map(Long::valueOf).collect(Collectors.toList());
    }

    public static String generateStringIds(List<Long> ids) {
        Set<String> headerIds = ids
                .stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        return String.join(",", headerIds);
    }

}
