package com.hand.demo.infra.util;

import java.lang.reflect.Field;

/**
 * Utils
 */
public class Utils {
    private Utils() {}
    public static <T> void populateNullFields(T source, T target) {
        Class<?> currentClass = source.getClass();
        while (currentClass != null) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object targetValue = field.get(target);
                    Object sourceValue = field.get(source);
                    if (targetValue == null && sourceValue != null) {
                        field.set(target, sourceValue);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error copying non-null fields", e);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }
}
