package io.fdeitylink.keroedit.util;

public final class NullArgumentException extends NullPointerException {
    public NullArgumentException() {
        super("Null value passed to function when null value is not permitted");
    }

    public NullArgumentException(final String funcName) {
        super("Null value passed to function " + funcName + "() when null value is not permitted");
    }

    public NullArgumentException(final String funcName, final String argName) {
        super("Null value passed for argument " + argName + " to function " + funcName + "() when null value is not permitted");
    }

    public static <T> T requireNonNull(final T obj) {
        return requireNonNull(obj, null, null);
    }

    public static <T> T requireNonNull(final T obj, final String funcName) {
        return requireNonNull(obj, funcName, null);
    }

    public static <T> T requireNonNull(final T obj, final String funcName, final String argName) {
        if (null == obj) {
            throw new NullArgumentException(funcName, argName);
        }
        return obj;
    }
}