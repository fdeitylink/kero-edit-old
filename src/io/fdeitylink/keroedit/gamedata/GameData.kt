package io.fdeitylink.keroedit.gamedata

import java.text.MessageFormat

import java.io.File

import java.nio.file.Path
import java.nio.file.Paths

import java.nio.file.Files

import java.io.IOException

import java.nio.file.NoSuchFileException

import javafx.collections.ObservableList
import javafx.collections.ListChangeListener

import javafx.application.Platform

import io.fdeitylink.util.Logger

import io.fdeitylink.util.baseFilename

import io.fdeitylink.util.ValidatedObservableList

import io.fdeitylink.keroedit.Messages

import io.fdeitylink.keroedit.map.PxPack

object GameData {
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

    private var _modType: ModType? = null
    val modType: ModType
        get() {
            checkInit("modType")
            return _modType!!
        }

    private var _executable: Path? = null
    val executable: Path
        get() {
            checkInit("executable")
            return _executable!!
        }

    private var _resourceFolder: Path? = null
    val resourceFolder: Path
        get() {
            checkInit("resourceFolder")
            return _resourceFolder!!
        }

    /*
     * TODO:
     * Use ObservableSets?
     * Get rid of the backing properties if possible
     * Store the items in the localize folder in these lists? (separate lists?)
     * Replace the public properties that have backing properties with methods? (since they can throw)
     */

    private val _bgms = FileList()
    val bgms: ObservableList<Path>
        get() {
            checkInit("bgms")
            return _bgms
        }

    private val _maps = FileList()
    val maps: ObservableList<Path>
        get() {
            checkInit("maps")
            return _maps
        }

    private val _images = FileList()
    val images: ObservableList<Path>
        get() {
            checkInit("images")
            return _images
        }

    private val _sfx = FileList()
    val sfx: ObservableList<Path>
        get() {
            checkInit("sfx")
            return _sfx
        }

    private val _scripts = FileList()
    val scripts: ObservableList<Path>
        get() {
            checkInit("scripts")
            return _scripts
        }

    @Throws(IOException::class)
    fun init(executable: Path) {
        wipe()

        val exe = executable.toAbsolutePath()

        require(exe.toString().endsWith(".exe"))
        { "GameData must be initialized with an executable file ending in \".exe\" (executable: $exe)" }

        require(Files.exists(exe)) { "GameData must be initialized with an executable file that exists (executable: $exe)" }

        _executable = exe

        /*
         * TODO:
         * Use path matching?
         * Ask which rsc folder to use if both are present
         */
        try {
            Files.newDirectoryStream(exe.parent, { Files.isDirectory(it) }).use {
                for (p in it) {
                    if (p.endsWith("rsc_k")) {
                        _resourceFolder = p.toAbsolutePath()
                        _modType = ModType.KERO_BLASTER
                        break
                    }
                    else if (p.endsWith("rsc_p")) {
                        _resourceFolder = p.toAbsolutePath()
                        _modType = ModType.PINK_HOUR //TODO: Find a way to detect if it's Pink Heaven, or ask the user
                        break
                    }
                }
            }
        }
        catch (except: IOException) {
            wipe()
            throw IOException(MessageFormat.format(Messages["GameData.DirStreamIOExcept.MESSAGE"], exe), except)
        }

        if (null == _modType) {
            //rsc folder is missing
            wipe()
            throw NoSuchFileException(exe.toString(), null, Messages["GameData.MISSING_RSC"])
        }

        try {
            fillFileList(_bgms, bgmFolder, bgmExtension)

            fillFileList(_maps, mapFolder, mapExtension)

            //TODO: Separate image lists (tilesets, spritesheets, etc.)
            fillFileList(_images, imageFolder, imageExtension)

            fillFileList(_sfx, sfxFolder, sfxExtension)

            fillFileList(_scripts, scriptFolder, scriptExtension)
        }
        catch (except: IOException) {
            wipe()
            throw except
        }

        isInitialized = true
    }

    /**
     * Wipes all data stored by this object. All [ObservableList]s
     * stored by this object are cleared, and [isInitialized] is set
     * to false.
     */
    fun wipe() {
        isInitialized = false

        _modType = null

        _executable = null
        _resourceFolder = null

        _bgms.clear()
        _maps.clear()
        _images.clear()
        _sfx.clear()
        _scripts.clear()
    }

    //TODO: Put a similar method into Utils.kt that returns a List?
    /**
     * Fills a given [FileList] with [Path] objects representing all of the files with the
     * given extension that were present in the folder within [_resourceFolder] denoted by
     * [pathFromResource].
     */
    private fun fillFileList(fileList: FileList, pathFromResource: String, extension: String) {
        val basePath: Path = Paths.get(_resourceFolder.toString() + File.separatorChar + pathFromResource)

        try {
            Files.newDirectoryStream(basePath, '*' + extension).use {
                for (p in it) {
                    val fname = p.baseFilename(extension)
                    if (fname.length <= PxPack.Head.FILENAME_MAX_LEN && !fname.contains(' ')) {
                        /*
                         * In order to prevent exceptions with regard to JavaFX objects using the
                         * list, the item addition is done on the JavaFX thread, since it is expected
                         * init() is called on a separate thread.
                         */
                        Platform.runLater{ fileList.add(p.toAbsolutePath())}
                    }
                }
            }
        }
        catch (except: IOException) {
            throw IOException(MessageFormat.format(Messages["GameData.DirStreamIOExcept.MESSAGE"], _executable), except)
        }
    }

    /**
     * Throws an [IllegalStateException] if [isInitialized] is false, otherwise returns normally
     *
     * @param retrievedProperty the name of the property attempting to be
     * retrieved. It will be put into the message of the [IllegalStateException]
     * if one is thrown.
     */
    private fun checkInit(retrievedProperty: String) {
        if (!isInitialized) {
            throw IllegalStateException("GameData must be initialized before $retrievedProperty can be retrieved from it")
        }
    }

    private class FileList: ValidatedObservableList<Path>(
            { "Paths added must have lengths <= ${PxPack.Head.FILENAME_MAX_LEN} and contain no spaces (path: ${it.toAbsolutePath()})" },
            { val fname = it.baseFilename(); fname.length <= PxPack.Head.FILENAME_MAX_LEN && !fname.contains(' ') }) {

        companion object {
            val deleteListener = ListChangeListener <Path> {
                if (isInitialized) {
                    while (it.next()) {
                        if (it.wasRemoved()) {
                            val removed = it.removed
                            for (p in removed) {
                                try {
                                    Files.deleteIfExists(p)
                                    //TODO: Why aren't it.list and _maps the same object?
                                    if (p.fileName.toString().endsWith(mapExtension)/*it.list === GameData._maps*/) {
                                        val scriptPath = Paths.get(_resourceFolder.toString() +
                                                                   File.separatorChar + scriptFolder + File.separatorChar +
                                                                   p.baseFilename(mapExtension) + scriptExtension)
                                        try {
                                            Files.deleteIfExists(scriptPath)
                                        }
                                        catch(except: IOException) {
                                            //Other than logging, fail silently as not deleting a file isn't a big deal
                                            Logger.logThrowable("Exception when attempting to delete script file ${p.toAbsolutePath()}", except)
                                        }
                                    }
                                }
                                catch (except: IOException) {
                                    //Other than logging, fail silently as not deleting a file isn't a big deal
                                    Logger.logThrowable("Exception when attempting to delete path ${p.toAbsolutePath()}", except)
                                }
                            }
                        }
                    }
                }
            }
        }

        init {
            addListener(deleteListener)
        }
    }
}
