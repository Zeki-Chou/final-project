package com.hand.demo.infra.util;

import java.lang.reflect.Field;

/**
 * Utils
 */
public class Utils {
    private Utils() {}
    public static <T> void populateNullFields(T source, T target) {
        Field[] fields = source.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object targetValue = field.get(target);
                if (targetValue == null) {
                    Object sourceValue = field.get(source);
                    field.set(target, sourceValue);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error copying non-null fields", e);
            }
        }
    }
}
