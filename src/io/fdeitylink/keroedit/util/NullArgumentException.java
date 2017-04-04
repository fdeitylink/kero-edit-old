package io.fdeitylink.keroedit.util;

public final class NullArgumentException extends NullPointerException {
    public NullArgumentException() {
        super("Null value passed to function when null value is not allowed");
    }

    public NullArgumentException(final String funcName) {
        super("Null value passed to function " + funcName + "() when null value is not allowed");
    }

    public NullArgumentException(final String funcName, final String argName) {
        super("Null value passed for argument " + argName + " to function " + funcName + "() when null value is not allowed");
    }
}