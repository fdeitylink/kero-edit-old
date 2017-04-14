package io.fdeitylink.keroedit.util;

import java.util.EnumMap;

public interface SafeOrdinalEnum <E extends Enum <E>> {
    //TODO: Make protected (Java 9)
    default EnumMap <E, Integer> ordinalMap(final Class <E> enumClass) {
        final EnumMap <E, Integer> map = new EnumMap <>(enumClass);

        int i = 0;
        for (final E x : enumClass.getEnumConstants()) {
            map.put(x, i++);
        }

        return map;
    }

    //static EnumMap <E, Integer> ordinalMap();
}