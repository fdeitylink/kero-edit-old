package io.fdeitylink.util

//TODO: Break up into MathUtils.kt, EnumUtils.kt

import java.util.EnumSet

import java.nio.file.Path
import java.nio.file.Files

import kotlin.reflect.KClass

fun Double.bound(lower: Double, upper: Double) = Math.max(lower, Math.min(this, upper))

fun Float.bound(lower: Float, upper: Float) = Math.max(lower, Math.min(this, upper))

fun Long.bound(lower: Long, upper: Long) = Math.max(lower, Math.min(this, upper))

fun Int.bound(lower: Int, upper: Int) = Math.max(lower, Math.min(this, upper))

fun Short.bound(lower: Short, upper: Short) = maxOf(lower, minOf(this, upper))

fun Byte.bound(lower: Byte, upper: Byte) = maxOf(lower, minOf(this, upper))

fun Path.baseFilename(ext: String = "."): String {
    if (Files.isDirectory(this)) {
        throw IllegalArgumentException("Attempt to get base filename of Path that does not represent a file")
    }

    val fname = this.fileName.toString()
    val extIndex = fname.lastIndexOf(ext)

    return if (-1 == extIndex) fname else fname.substring(0, extIndex)
}

//TODO: Make the following two methods regular, non-extension methods?
fun <E> EnumSet<E>.encoded(): Long where E: Enum<E>, E: SafeEnum<E> {
    var flags = 0L
    for (e in this) {
        flags = flags or (1L shl e.ordinal)
    }

    return flags
}

fun <E> Long.decoded(enumClass: KClass<E>): EnumSet<E> where E: Enum<E>, E: SafeEnum<E> {
    /*
     * Assumes at most 64 values in the enum class (64 bits in a long)
     * http://stackoverflow.com/a/2199486
     */

    //TODO: Throw exception if there are more than 64 values?

    val set = EnumSet.noneOf(enumClass.java)
    var ordinal = 0

    val constants: Array<out E> = enumClass.java.enumConstants

    /*
     * Bitshift through every constant in the enum
     * and check the flag in the encoded Long. If it
     * is set, then add the enum constant to the set
     */

    //TODO: Possible to do this as a for loop?
    var i = 1L
    while (i != 0L && ordinal < constants.size) {
        if (0L != (i and this)) {
            set.add(constants[ordinal])
        }
        i = i shl 1
        ordinal++
    }

    return set
}

/**
 * Executes the given [block] function on this resource and then closes it
 * down correctly whether an exception is thrown or not. Ripped from the
 * [Closeable][java.io.Closeable] extension method defined in [kotlin.io].
 *
 * @param block a function to process this [AutoCloseable] resource.
 * @return the result of [block] function invoked on this resource.
 */
inline fun <T: AutoCloseable, R> T.use(block: (T) -> R): R {
    var closed = false
    try {
        return block(this)
    }
    catch (except: Exception) {
        closed = true
        try {
            this.close()
        }
        catch (closeExcept: Exception) {
            except.addSuppressed(closeExcept)
        }
        throw except
    }
    finally {
        if (!closed) {
            this.close()
        }
    }
}