package io.fdeitylink.keroedit.util;

import java.util.EnumMap;

public interface SafeOrdinalEnum <E extends Enum <E>> {
    //TODO: Does this even count as "safe"? Should enums be constructed with int serving as indexes instead?
    //TODO: Make protected (Java 9) and static (impossible I think)
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