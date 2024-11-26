package com.hand.demo.infra.util;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Utils
 */
public class Utils {
    private Utils() {}

    public static String[] getNonNullFields(Object dto, String... fieldNames) {
        List<String> nonNullFields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            try {
                Field field = dto.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.get(dto) != null) {
                    nonNullFields.add(fieldName);

                }
            } catch (Exception e) {
                System.out.println("wkwkwkwkwk: " + e.getMessage());
                return new String[0];
            }
        }
        return nonNullFields.toArray(new String[0]);
    }

    public static CustomUserDetails getCurrentUser() {
        return DetailsHelper.getUserDetails();
    }
}
