package io.fdeitylink.util

import java.util.function.UnaryOperator

import java.nio.file.Path
import java.nio.file.Files

/**
 * Returns the filename of this [Path] sans the ending [extension]
 *
 * @receiver a [Path] that represents a file
 *
 * @param extension the ending extension to exempt from the returned
 * filename [String]
 *
 * @return the filename of this [Path] sans the ending [extension]
 *
 * @throws IllegalArgumentException if the receiving [Path] does not
 * represent a file
 */
fun Path.baseFilename(extension: String = "."): String {
    require(!Files.isDirectory(this)) { "Receiver Path does not represent a file (path: $this)" }

    val fname = this.fileName.toString()
    val extIndex = fname.lastIndexOf(extension)

    return if (-1 == extIndex) fname else fname.substring(0, extIndex)
}

/**
 * Executes the given [block] function on the receiving resource and then
 * closes it down correctly whether an exception is thrown or not. Ripped
 * from the [Closeable][java.io.Closeable] extension method defined in [kotlin.io].
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

open class ValidatedList<E> private constructor(private val validator: (E) -> Boolean,
                                                private val lazyMessage: () -> Any,
                                                private val delegateList: MutableList <E>
                                               ): MutableList<E> by delegateList {

    //TODO: Put what the invalid value is into the default lazyMessage
    constructor(validator: (E) -> Boolean,
                lazyMessage: () -> Any = { "Invalid value attempted to be added to list" }
               ): this(validator, lazyMessage, mutableListOf())

    override fun add(element: E): Boolean {
        require(validator(element), lazyMessage)
        return delegateList.add(element)
    }

    override fun add(index: Int, element: E) {
        require(validator(element), lazyMessage)
        return delegateList.add(index, element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        for (e in elements) {
            require(validator(e), lazyMessage)
        }
        return delegateList.addAll(elements)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        for (e in elements) {
            require(validator(e), lazyMessage)
        }
        return delegateList.addAll(index, elements)
    }

    override fun replaceAll(operator: UnaryOperator<E>) {
        for (e in delegateList) {
            require(validator(operator.apply(e)), lazyMessage)
        }
        return delegateList.replaceAll(operator)
    }

    override fun set(index: Int, element: E): E {
        require(validator(element), lazyMessage)
        return delegateList.set(index, element)
    }
}