package io.fdeitylink.util

import java.nio.file.Path
import java.nio.file.Files

fun Path.baseFilename(ext: String = "."): String {
    if (Files.isDirectory(this)) {
        throw IllegalArgumentException("Attempt to get base filename of Path that does not represent a file")
    }

    val fname = this.fileName.toString()
    val extIndex = fname.lastIndexOf(ext)

    return if (-1 == extIndex) fname else fname.substring(0, extIndex)
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