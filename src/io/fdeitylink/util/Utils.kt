package io.fdeitylink.util

import java.util.EnumSet

import java.nio.file.Path
import java.nio.file.Files

import kotlin.reflect.KClass

fun Double.bound(lower: Double, upper: Double) = Math.max(lower, Math.min(this, upper))

fun Float.bound(lower: Float, upper: Float) = Math.max(lower, Math.min(this, upper))

fun Long.bound(lower: Long, upper: Long) = Math.max(lower, Math.min(this, upper))

fun Int.bound(lower: Int, upper: Int) = Math.max(lower, Math.min(this, upper))

fun Short.bound(lower: Short, upper: Short) = max(lower, min(this, upper))

fun Byte.bound(lower: Byte, upper: Byte) = max(lower, min(this, upper))

fun max(a: Short, b: Short) = if (a >= b) a else b

fun min(a: Short, b: Short) = if (a <= b) a else b

fun max(a: Byte, b: Byte) = if (a >= b) a else b

fun min(a: Byte, b: Byte) = if (a <= b) a else b

fun Path.baseFilename(ext: String = "*"): String {
    if (Files.isDirectory(this)) {
        throw IllegalArgumentException("Attempt to get base filename of Path that does not represent a file")
    }

    val fname = this.fileName.toString()
    val extIndex = fname.lastIndexOf(ext)

    return if (-1 == extIndex) fname else fname.substring(0, extIndex)
}

//TODO: Make the following two methods regular, non-extension methods?
fun <E> EnumSet<E>.encoded(): Long where E : Enum<E>, E : SafeEnum<E> {
    var flags = 0L
    for (e in this) {
        flags = flags or (1L shl e.ordinal)
    }

    return flags
}

fun <E> Long.decode(enumClass: KClass<E>): EnumSet<E> where E : Enum<E>, E : SafeEnum<E> {
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