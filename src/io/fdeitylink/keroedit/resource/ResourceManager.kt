package io.fdeitylink.keroedit.resource

import java.nio.file.Path
import java.nio.file.Paths

import java.nio.file.FileSystem
import java.nio.file.FileSystems

import java.io.InputStream

import java.nio.file.FileSystemNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException

import javafx.scene.image.Image

object ResourceManager {
    //TODO: Make lateinit?
    private var jarFS: FileSystem? = null

    private val EMPTY_IMAGE = Image("file:///")

    /**
     * Returns a [Path] object representing a file that exists in the
     * [resource][io.fdeitylink.keroedit.resource] package. For example, if the
     * given filename is "abc" and such a file or directory exists in the
     * [resource][io.fdeitylink.keroedit.resource] package, then a [Path] object
     * would be returned that represents that file or directory. Should the file
     * or directory not exist, or there be an issue retrieving it, null will be
     * returned.
     *
     * @param filename a [String] representing the name of a file or directory
     * to get a [Path] for
     *
     * @return a [Path] object representing a file or directory denoted by [filename],
     * or null if there were any problems retrieving the file other than it not existing
     *
     * @throws NoSuchFileException if the file denoted by [filename] does not exist
     * in the [resource][io.fdeitylink.keroedit.resource] package
     */
    fun getPath(filename: String): Path? {
        val uri = javaClass.getResource(filename)?.toURI() ?: throw NoSuchFileException(filename)
        try {
            return Paths.get(uri).toAbsolutePath()
        }
        catch (except: FileSystemNotFoundException) {
            if (null != jarFS) {
                return null
            }
            try {
                /*
                 * TODO:
                 * Does jarFS need to point to the root of the resource package?
                 * Close the filesystem at some point? (though it needs to stay open for using the retrieved Path)
                 */
                //http://stackoverflow.com/a/25033217
                val environment = mapOf(Pair("create", "true"))
                jarFS = FileSystems.newFileSystem(uri, environment)
                return Paths.get(uri).toAbsolutePath()
            }
            catch (except: IOException) {
                return null
            }
        }
    }

    /**
     * Returns an [InputStream] allowing access to a file in the
     * [resource][io.fdeitylink.keroedit.resource] package.
     *
     * @param filename the name of the file in the
     * [resource][io.fdeitylink.keroedit.resource] package to get an [InputStream] for.
     *
     * @return an [InputStream] that gives access to a file represented by [filename]
     */
    fun getInputStream(filename: String): InputStream? = javaClass.getResourceAsStream(filename)

    /**
     * Returns an [Image] stored in a file with a given filename
     *
     * @param filename the name of the image file to get an [Image] of
     *
     * @return an [Image] that represents the file denoted by [filename]
     */
    fun getImage(filename: String): Image {
        val p = getPath(filename) ?: return EMPTY_IMAGE
        return Image(p.toUri().toString(), false)
    }
}