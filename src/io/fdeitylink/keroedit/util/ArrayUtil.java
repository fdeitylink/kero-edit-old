package io.fdeitylink.keroedit.util;

import java.lang.reflect.Array;

public final class ArrayUtil {
    private ArrayUtil() {

    }

    public static <T> void copyNDimensional(final T src, final T dest) {
        if (!src.getClass().isArray() || !dest.getClass().isArray()) {
            throw new IllegalArgumentException("Attempt to copy n-dimensional array when at least one argument is not an array");
        }

        final int srcDimensions = getNumDimensions(src);
        final int destDimensions = getNumDimensions(dest);
        if (srcDimensions != destDimensions) {
            throw new IllegalArgumentException("Attempt to copy src into dest when they have an unequal amount of dimensions " +
                                               "(src: " + srcDimensions + ", " + "dest: " + destDimensions + ')');
        }

        final int srcLen = Array.getLength(src);
        final int destLen = Array.getLength(dest);
        if (srcLen != destLen) {
            throw new IllegalArgumentException("Attempt to copy src into dest when at least one of their lengths are unequal " +
                                               "(src: " + srcLen + ", " + "dest: " + destLen + ')');
        }

        //Base case: 1D array
        if (1 == srcDimensions) {
            System.arraycopy(src, 0, dest, 0, destLen);
            return;
        }

        /*
         * src & dest are still multi-dimensional, so
         * copy elements of every sub-array in src into
         * each sub-array in dest.
         */
        for (int i = 0; i < destLen; ++i) {
            //TODO: Make non-recursive (is this even possible?)
            copyNDimensional(Array.get(src, i), Array.get(dest, i));
        }
    }

    public static int getNumDimensions(final Object arr) {
        Class <?> componentType = arr.getClass().getComponentType();
        for (int n = 0; ; ++n) {
            /*
             * Calling getComponentType() on a non-array Class returns null.
             * When we've reached that point, we have the number of dimensions
             * (how many non-null component types there were prior)
             * e.g. int[][].class.getComponentType() -> int[]
             *      int[].class.getComponentType()   -> int
             *      int.class.getComponentType()     -> null
             */
            if (null == componentType) {
                return n;
            }
            componentType = componentType.getComponentType();
        }
    }
}