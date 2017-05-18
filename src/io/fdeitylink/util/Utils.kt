package io.fdeitylink.util

import java.nio.file.Path
import java.nio.file.Files

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