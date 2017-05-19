package io.fdeitylink.keroedit.resource

import java.nio.file.Path
import java.nio.file.Paths

import java.nio.file.FileSystem
import java.nio.file.FileSystems

import java.io.InputStream

import java.nio.file.FileSystemNotFoundException
import java.io.IOException

import javafx.scene.image.Image

object ResourceManager {
    private var jarFS: FileSystem? = null
    private val EMPTY_IMAGE = Image("file:///")

    fun getPath(filename: String): Path? {
        val uri = javaClass.getResource(filename).toURI()
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
                val environment = HashMap<String, String>()
                environment.put("create", "true")
                jarFS = FileSystems.newFileSystem(uri, environment)
                return Paths.get(uri).toAbsolutePath()
            }
            catch (except: IOException) {
                return null
            }
        }
    }

    fun getInputStream(filename: String): InputStream? = javaClass.getResourceAsStream(filename)

    fun getImage(filename: String): Image {
        val p = getPath(filename) ?: return EMPTY_IMAGE
        return Image(p.toUri().toString(), false)
    }
}