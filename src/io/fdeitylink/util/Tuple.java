/*
 * TODO:
 * Make immutable?
 * Truly create tuple ("infinite" items)
 */

package io.fdeitylink.util;

public final class Tuple <X, Y> {
    public X x;
    public Y y;

    public Tuple(final X x, final Y y) {
        this.x = x;
        this.y = y;
    }
}