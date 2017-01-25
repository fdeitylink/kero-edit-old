package com.fdl.keroedit.util;

import java.lang.reflect.Method;

import java.lang.reflect.InvocationTargetException;

/**
 * Convenience class for storing a {@code Method} with arguments to pass it
 * for later calling of Method.invoke(). Its members are public as this class functions
 * more as a utility storage class.
 */
public class MethodStore {
    /**
     * The {@code Object} to invoke the method upon
     */
    public final Object object;

    /**
     * The {@code Method} this object stores
     */
    public final Method method;

    /**
     * The arguments for {@code method}
     */
    public final Object[] args;

    /**
     * Constructs a {@code MethodStorage} object
     *
     * @param invokeObj The object upon which the method
     *                  will be invoked (can be null for static methods)
     * @param method    The Method that should be stored
     * @param args      The arguments that should be stored
     */
    public MethodStore(Object invokeObj, Method method, Object... args) {
        this.method = method;
        this.object = invokeObj;
        this.args = args;
    }

    /**
     * Convenience method for invoking the method stored by this object
     *
     * @return The result of calling calling invoke() on the stored Method
     * object and passing it the stored object to be invoked upon
     * and the stored arguments to pass it.
     *
     * @throws InvocationTargetException If the method that the stored Method
     *                                   object references throws an exception.
     *                                   {@code getCause()} can be called on this
     *                                   exception to get the actual exception thrown
     *                                   by the called method.
     */
    public Object invoke() throws InvocationTargetException {
        if (null != method) {
            try {
                return method.invoke(object, args);
            }
            catch (final IllegalAccessException except) {
                Logger.logException("Failed to invoke method " + method, except);
            }
        }
        return null;
    }
}