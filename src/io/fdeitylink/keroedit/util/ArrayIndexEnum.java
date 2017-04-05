package io.fdeitylink.keroedit.util;

import java.util.EnumMap;

public interface ArrayIndexEnum <E extends Enum <E>> {
    //TODO: Make protected (Java 9)
    default EnumMap <E, Integer> enumMap(final Class <E> enumClass) {
        final EnumMap <E, Integer> enumMap = new EnumMap <>(enumClass);

        int i = 0;
        for (final E x : enumClass.getEnumConstants()) {
            enumMap.put(x, i++);
        }

        return enumMap;
    }
}