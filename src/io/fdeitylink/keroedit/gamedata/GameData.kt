package io.fdeitylink.keroedit.gamedata

import java.text.MessageFormat

import java.io.File

import java.nio.file.Path
import java.nio.file.Paths

import java.nio.file.Files

import java.io.IOException

import java.nio.file.NoSuchFileException

import javafx.collections.FXCollections
import javafx.collections.ObservableList

import io.fdeitylink.util.NotNull

import io.fdeitylink.util.baseFilename

import io.fdeitylink.keroedit.Messages

import io.fdeitylink.keroedit.map.PxPack

object GameData {
    //TODO: Insert File.separatorChar into the folder strings?
    const val bgmFolder = "bgm"
    const val bgmExtension = ".ptcop"

    const val mapFolder = "field"
    const val mapExtension = ".pxpack"

    const val imageFolder = "img"
    const val imageExtension = ".png"

    //const val pxAttrFolder = imageFolder
    const val pxAttrExtension = ".pxattr"

    const val sfxFolder = "se"
    const val sfxExtension = ".ptnoise"

    const val scriptFolder = "text"
    const val scriptExtension = ".pxeve"

    var isInitialized = false
        private set

    //TODO: Use Delegates.nonNull? Derive from it (it doesn't allow setting to null)
    var modType: ModType? = null
        @NotNull get() {
            checkInit("modType")
            return field!!
        }
        private set

    var executable: Path? = null
        @NotNull get() {
            checkInit("executable")
            return field!!
        }
        private set

    var resourceFolder: Path? = null
        @NotNull get() {
            checkInit("resourceFolder")
            return field!!
        }
        private set

    var bgms: ObservableList<Path>? = null
        @NotNull get() {
            checkInit("bgms")
            return field!!
        }
        private set

    var maps: ObservableList<Path>? = null
        @NotNull get() {
            checkInit("maps")
            return field!!
        }
        private set

    var images: ObservableList<Path>? = null
        @NotNull get() {
            checkInit("images")
            return field!!
        }
        private set

    var sfx: ObservableList<Path>? = null
        @NotNull get() {
            checkInit("sfx")
            return field!!
        }
        private set

    var scripts: ObservableList<Path>? = null
        @NotNull get() {
            checkInit("scripts")
            return field!!
        }
        private set

    @Throws(IOException::class)
    fun init(executable: Path) {
        wipe()

        val exe = executable.toAbsolutePath()

        if (!exe.fileName.toString().endsWith(".exe")) {
            throw IllegalArgumentException("GameData must be initialized with an executable file with the extension \".exe\" " +
                                           "(exe: $exe)")
        }

        if (!Files.exists(exe)) {
            //TODO: Throw IllegalArgumentException?
            throw NoSuchFileException(exe.toString(), null, "Mod exe does not exist")
        }

        this.executable = exe

        /*
         * Although we've barely started initializing, this statement is necessary.
         * The property getters throw an exception if isInitialized is false, so
         * set it to true here so those getters can be used as needed during the
         * initialization process. Any time that initialization fails for any
         * reason, isInitialized is set to false via wipe().
         */
        isInitialized = true

        /*
         * TODO:
         * Use path matching?
         * Ask which rsc folder to use if both are present
         */
        try {
            Files.newDirectoryStream(exe.parent, { Files.isDirectory(it) }).use {
                for (p in it) {
                    if (p.endsWith("rsc_k")) {
                        resourceFolder = p.toAbsolutePath()
                        modType = ModType.KERO_BLASTER
                        break
                    }
                    else if (p.endsWith("rsc_p")) {
                        resourceFolder = p.toAbsolutePath()
                        modType = ModType.PINK_HOUR //TODO: Find a way to detect if it's Pink Heaven, or ask the user
                        break
                    }
                }
            }
        }
        catch (except: IOException) {
            wipe()
            throw IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"), exe), except)
        }

        if (null == modType) {
            //rsc folder is missing
            wipe()
            throw NoSuchFileException(executable.toString(), null, "rsc_x folder does not exist")
        }

        try {
            bgms = fileList(bgmFolder, bgmExtension)

            maps = fileList(mapFolder, mapExtension)

            /*maps.addListener(ListChangeListener<Path> { c: ListChangeListener.Change<out Path> ->
                val removed = c.removed
                //TODO: Should I go back to returning immutable ObservableLists in getX() and provide removeX() methods?
                for (p in removed) {
                    try {
                        Files.deleteIfExists(p)
                    }
                    catch (except: IOException) {
                        //TODO: Something...
                    }
                }
            })*/

            //TODO: Separate image lists (tilesets, spritesheets, etc.)
            images = fileList(imageFolder, imageExtension)

            sfx = fileList(sfxFolder, sfxExtension)

            scripts = fileList(scriptFolder, scriptExtension)
        }
        catch (except: IOException) {
            wipe()
            throw except
        }
    }

    /**
     * Clears all data stored by this object
     */
    fun wipe() {
        isInitialized = false

        modType = null

        executable = null
        resourceFolder = null

        bgms = null
        maps = null
        images = null
        sfx = null
        scripts = null
    }

    private fun fileList(pathFromResource: String, ext: String): ObservableList<Path> {
        val basePath: Path = Paths.get(resourceFolder.toString() + File.separatorChar + pathFromResource)

        val list = FXCollections.observableArrayList(ArrayList<Path>())

        try {
            Files.newDirectoryStream(basePath, '*' + ext).use {
                for (p in it) {
                    val fname = p.baseFilename(ext)
                    if (fname.length <= PxPack.Head.FILENAME_MAX_LEN && !fname.contains(' ')) {
                        list.add(p)
                    }
                }
            }
        }
        catch (except: IOException) {
            throw IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept"), executable), except)
        }

        return list
    }

    /**
     * Throws an [IllegalStateException] if [isInitialized] is false, otherwise returns normally
     */
    private fun checkInit(retrievedItem: String) {
        if (!isInitialized) {
            throw IllegalStateException("GameData must be initialized before $retrievedItem can be retrieved from it")
        }
    }

    enum class ModType {
        PINK_HOUR,
        PINK_HEAVEN,
        KERO_BLASTER
    }
}