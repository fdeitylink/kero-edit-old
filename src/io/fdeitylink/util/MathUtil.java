package io.fdeitylink.util;

public final class MathUtil {
    private MathUtil() {

    }

    /**
     * Returns either the given number or
     * the lower limit if the number is less than the lower limit or
     * the higher limit if the number is greater than the higher limit.
     *
     * @param num The number to put within the given bounds
     * @param lower The inclusive lower limit of the number
     * @param upper The inclusive upper limit of the number
     *
     * @return A number within the specified bounds
     */
    public static int bound(final int num, final int lower, final int upper) {
        //TODO: Overload for float, double, long
        //TODO: Follow convention of lower being inclusive, upper being exclusive
        return Math.max(lower, Math.min(num, upper));
    }
}