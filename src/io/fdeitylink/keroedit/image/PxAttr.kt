package io.fdeitylink.keroedit.image

import java.text.MessageFormat

import java.nio.file.Path
import java.nio.file.Files

import java.nio.file.StandardOpenOption

import java.nio.charset.Charset

import java.nio.ByteBuffer
import java.nio.ByteOrder

import java.text.ParseException
import java.io.IOException

import kotlin.experimental.and

import io.fdeitylink.util.Array2D

import io.fdeitylink.util.Logger

import io.fdeitylink.keroedit.Messages

class PxAttr {
    companion object {
        //TODO: Create default Array2D<Int> for attributes with all empty values
        private const val HEADER_STRING = "pxMAP01\u0000"
    }

    private val path: Path

    /*
     * TODO:
     * Rather than making this nullable, use an Array2D<Int> with width and height of 0
     *  - Or with width and height of 16 and all attributes set to 0?
     * Somehow make this a val (is a primary constructor needed for this?)
     */
    private var _attributes: Array2D<Int>? = null
    val attributes: Array2D<Int>?
        get() {
            val attrs = _attributes
            return if (null == attrs) null else Array2D(attrs.width, attrs.height) { x, y -> attrs[x, y] }
        }

    @Suppress("UsePropertyAccessSyntax")
    constructor(inPath: Path) {
        path = inPath.toAbsolutePath()

        if (!Files.exists(path)) {
            //TODO: Rather than setting to null, save a new PxAttr file with empty attributes by calling save()
            _attributes = null
            return
        }

        try {
            Files.newByteChannel(path, StandardOpenOption.READ).use {
                var buf = ByteBuffer.allocate(HEADER_STRING.length)
                it.read(buf)

                if (String(buf.array()) != HEADER_STRING) {
                    throw ParseException(MessageFormat.format(Messages["PxAttr.INCORRECT_HEADER"], path),
                                         it.position().toInt())
                }

                buf = ByteBuffer.allocate(4)
                buf.order(ByteOrder.LITTLE_ENDIAN)
                it.read(buf)
                buf.flip()

                //TODO: Validate dimensions are 16 by 16 or 0 by 0
                val width = buf.getShort().toInt()
                val height = buf.getShort().toInt()

                _attributes = if (width * height > 0) {
                    //TODO: Find if the skipped value is ever not 0 (might've already checked but make sure)
                    it.position(it.position() + 1)

                    buf = ByteBuffer.allocate(width * height)
                    it.read(buf)

                    //TODO: Use buf.array().forEachIndexed()
                    val bufArray = buf.array()
                    Array2D(width, height) { x, y -> (bufArray[(width * y) + x] and 0xFF.toByte()).toInt() }
                }
                else {
                    null
                }
            }
        }
        catch (except: IOException) {
            //TODO: Have PxAttrManager log and throw the specialized exception?
            Logger.logThrowable("Exception while parsing PXATTR file $path", except)
            throw IOException("${except.localizedMessage}\n$path", except)
        }
    }

    constructor(pxAttr: PxAttr, inPath: Path) {
        _attributes = pxAttr.attributes //Copies, they do not reference same object
        path = inPath.toAbsolutePath()
    }

    fun setAttribute(x: Int, y: Int, attribute: Int) {
        //TODO: Check attribute for validity (range)
        if (null == _attributes) {
            throw IndexOutOfBoundsException("attributes is empty")
        }
        _attributes!![x, y] = attribute
    }

    fun save() {
        try {
            Files.newByteChannel(path,
                                 StandardOpenOption.WRITE,
                                 StandardOpenOption.TRUNCATE_EXISTING,
                                 StandardOpenOption.CREATE).use {
                var buf = ByteBuffer.wrap(HEADER_STRING.toByteArray(Charset.forName("SJIS")))
                it.write(buf)

                val attrs = _attributes
                if (null == attrs) {
                    buf = ByteBuffer.allocate(4)
                    buf.putShort(0.toShort())
                            .putShort(0.toShort())
                    buf.flip()

                    it.write(buf)
                    return
                }
                else {
                    val width = attrs.width.toShort()
                    val height = attrs.height.toShort()

                    buf = ByteBuffer.allocate(5)
                    buf.order(ByteOrder.LITTLE_ENDIAN)

                    buf.putShort(width).putShort(height).put(0.toByte())
                    buf.flip()

                    it.write(buf)

                    buf = ByteBuffer.allocate((width and 0xFFFF.toShort()) * (height and 0xFFFF.toShort()))
                    attrs.forEach { buf.put(it.toByte()) }
                    buf.flip()
                    it.write(buf)
                }
            }
        }
        catch(except: IOException) {
            //TODO: Have PxAttrManager log and throw the specialized exception?
            Logger.logThrowable("Exception while saving PXATTR file $path", except)
            throw IOException("${except.localizedMessage}\n$path", except)
        }
    }
}