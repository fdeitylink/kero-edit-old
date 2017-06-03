package io.fdeitylink.util

import java.util.Arrays

/**
 * A class that simplifies implementing a rectangular two-dimensional array so as
 * to avoid confusing code that uses `Array<Array<T>>`.
 *
 * An `Array2D` should be considered as a series of _rows of elements_ rather than a
 * series of columns of elements, which matters for iterating over it, methods like
 * `get(y: Int)` and the primary constructor's parameters. Given the following 2D array
 *
 * ```
 * //Java
 * int[][] arr = {{0,  1,  2},
 *                {3,  4,  5},
 *                {6,  7,  8},
 *                {9, 10, 11}};
 * ```
 *
 * the height would be considered `4` and width `3`, and any iterating would pass through
 * 0, 1, and 2 before 3, 4, and 5, and so on.
 *
 * @param T the type of the element to be stored by this `Array2D`
 *
 * @constructor
 * Constructs a new `Array2D`, given its [width], [height], and a [backing] 2D array.
 * This constructor is useful for turning a regular Java 2D array (or `Array<Array<T>>`)
 * into an `Array2D`. For example, the following would create an `Array2D` with `regArray`
 * as its backing list.
 *
 * ```
 * //Java
 * String[][] regArray = {{"Hello", "World"}, {"Alice", "Bob"}};
 * Array2D<String> array2D = new Array2D<String>(regArray[0].length, regArray.length, regArray);
 * ```
 *
 * Most of the time, however, the [emptyArray2D], [array2DOfNulls], and [invoke]
 * methods should be used for constructing a new `Array2D`.
 *
 * @throws IllegalArgumentException if [height] does not match [backing.size][Array.size],
 * [width] does not match the width of at least one row of [backing], or [backing] is not
 * a purely rectangular array (the widths of its rows are inconsistent).
 *
 * @property width the width of this `Array2D`.
 *
 * @property height the height of this `Array2D`.
 *
 * @property backing the backing `Array<Array<T>>` of this `Array2D`. It can be used for
 * methods that expect a standard Java 2D array (or Kotlin methods not using this class).
 * For example:
 * ```
 * //Kotlin
 * methodTakingStandard2DArray(anArray2D.backing)
 * ```
 * would give `methodTakingStandard2DArray` direct access to the contents of this `Array2D`
 * (thereby allowing it to modify elements).
 */
class Array2D<T>(val width: Int, val height: Int, val backing: Array<Array<T>>) {
    init {
        if (backing.size != height) {
            throw IllegalArgumentException("height does not match height of backing (height: $height, backing: ${backing.size}")
        }

        if (backing.isNotEmpty()) {
            //TODO: Use backing[row - 1] rather than prevWidth?
            var prevWidth = backing[0].size

            for (row in backing) {
                if (row.size != prevWidth) {
                    throw IllegalArgumentException("width of one row of backing does not match width of previous (width: ${row.size}, previous width: $prevWidth)")
                }
                prevWidth = row.size
                if (row.size != width) {
                    throw IllegalArgumentException("width does not match width of at least one row of backing (width: $width, row: ${row.size})")
                }
            }
        }
    }

    /*
     * TODO:
     * Provide iterator() method and properties that standard Array has
     */

    companion object {
        /**
         * Constructs an `Array2D<T>` with the given [width] and [height], whose elements
         * are filled via the [init] function. [init] takes the x and y coordinates of the
         * element to be constructed and returns that element. Has the same purpose as the
         * [Array] constructor.
         *
         * This method allows one to write code such as
         * ```
         * Array2D(width, height) {...}
         * ```
         * which is looks equivalent to a constructor call, despite not being entirely
         * equivalent. A constructor cannot be used as [T] is a `reified` parameter, so
         * instead an inline `invoke` operator is used. Because [T] is `reified`, this
         * method cannot be called from Java code. In such cases where an `Array2D` must
         * be constructed in Java, the primary constructor for this class should be used.
         */
        inline operator fun <reified T> invoke(width: Int, height: Int, init: (x: Int, y: Int) -> (T)) =
                Array2D(width, height, Array(height, { y -> Array(width, { x -> init(x, y) }) }))

        /**
         * Returns a [String] representation of [array2D] equivalent to [Arrays.deepToString].
         */
        fun <T> toString(array2D: Array2D<T>): String = Arrays.deepToString(array2D.backing)
    }

    /**
     * Returns the row stored by this `Array2D` with index [y]. As an operator, this method
     * can also be called with the index operator:
     * ```
     * val row = array2D[y]
     * ```
     *
     * @throws IndexOutOfBoundsException if [y] is outside the bounds of this `Array2D`.
     */
    operator fun get(y: Int) = backing[y]

    /**
     * Returns the element stored by this `Array2D` with coordinates ([x], [y]). As an
     * operator, this method can also be called with the index operator:
     * ```
     * val value = array2D[x, y]
     * ```
     *
     * @throws IndexOutOfBoundsException if [x] or [y] are outside the bounds of
     * this `Array2D`.
     */
    operator fun get(x: Int, y: Int) = backing[y][x]

    /**
     * Sets the element in this `Array2D` with coordinates ([x], [y]) to [value]. As an operator,
     * this method can also be called with the index operator:
     * ```
     * array2D[x, y] = value
     *
     * @throws IndexOutOfBoundsException if [x] or [y] are outside the bounds of this `Array2D`.
     */
    operator fun set(x: Int, y: Int, value: T) {
        backing[y][x] = value
    }

    /**
     * Calls [action] on each element. Note that it will iterate through each element of each _row_,
     * not of each column.
     */
    inline fun forEach(action: (T) -> Unit) {
        backing.forEach { it.forEach { action.invoke(it) } }
    }

    /**
     * Calls [action] on each element, giving it the element as well as its x and y coordinates. Note
     * that it will iterate through each element of each _row_, not of each column. In other words,
     * x will increase before y does.
     */
    inline fun forEachIndexed(action: (x: Int, y: Int, T) -> Unit) {
        backing.forEachIndexed { y, row -> row.forEachIndexed { x, value -> action.invoke(x, y, value) } }
    }
}

/**
 * Constructs an `Array2D<T>` with a `width` and `height` of `0`. Has the same purpose
 * as [emptyArray].
 */
inline fun <reified T> emptyArray2D() = Array2D(0, 0, Array(0, { emptyArray<T>() }))

/**
 * Constructs an `Array2D<T>` with the given [width] and [height], with a backing
 * array whose elements are all `null`. Has the same purpose as [arrayOfNulls].
 */
inline fun <reified T> array2DOfNulls(width: Int, height: Int) =
        Array2D(width, height, Array(height, { arrayOfNulls<T>(width) }))

/**
 * Constructs an `Array2D<T>` with the given [width] and [elements]. Has the same
 * purpose as [arrayOf].
 *
 * If the width were `3`, for example, then the first three elements given would
 * go into the first row of the `Array2D`, the next three the second row, and so on.
 *
 * @throws IllegalArgumentException if [elements.size][Array.size] is not a multiple
 * of [width].
 */
inline fun <reified T> array2DOf(width: Int, vararg elements: T): Array2D<T> {
    if (elements.size % width != 0) {
        throw IllegalArgumentException("elements cannot be converted to Array2D because its size is not divisible by width (width: $width, elements: ${elements.size})")
    }

    return Array2D(width, elements.size / width) { x, y -> elements[(width * y) + x] }
}