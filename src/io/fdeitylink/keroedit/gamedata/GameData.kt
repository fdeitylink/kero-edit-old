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

import io.fdeitylink.util.baseFilename

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
     * Find a way to get rid of the backing properties
     * Don't return unmodifiable ObservableLists?
     *  - Bind ListChangeListeners instead and throw excepts for invalid changes?
     * Make use of the unused lists
     * Store the items in the localize folder in these lists?
     * Replace the public properties that have backing properties with methods? (since they can throw)
     */

    private val _bgms: ObservableList<Path> = FXCollections.observableArrayList()
    val bgms: ObservableList<Path>
        get() {
            checkInit("bgms")
            return FXCollections.unmodifiableObservableList(_bgms)
        }

    private val _maps: ObservableList<Path> = FXCollections.observableArrayList()
    val maps: ObservableList<Path>
        get() {
            checkInit("maps")
            return FXCollections.unmodifiableObservableList(_maps)
        }

    private val _images: ObservableList<Path> = FXCollections.observableArrayList()
    val images: ObservableList<Path>
        get() {
            checkInit("images")
            return FXCollections.unmodifiableObservableList(_images)
        }

    private val _sfx: ObservableList<Path> = FXCollections.observableArrayList()
    val sfx: ObservableList<Path>
        get() {
            checkInit("sfx")
            return FXCollections.unmodifiableObservableList(_sfx)
        }

    private val _scripts: ObservableList<Path> = FXCollections.observableArrayList()
    val scripts: ObservableList<Path>
        get() {
            checkInit("scripts")
            return FXCollections.unmodifiableObservableList(_scripts)
        }

    @Throws(IOException::class)
    fun init(executable: Path) {
        wipe()

        val exe = executable.toAbsolutePath()

        require(exe.fileName.toString().endsWith(".exe"))
        { "GameData must be initialized with an executable file ending in \".exe\" (executable: $exe)" }

        if (!Files.exists(exe)) {
            throw NoSuchFileException(exe.toString(), null, "Mod exe does not exist (executable: $exe)")
        }

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
            throw IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"), exe), except)
        }

        if (null == _modType) {
            //rsc folder is missing
            wipe()
            throw NoSuchFileException(exe.toString(), null, "rsc_x folder does not exist")
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
     * Clears all data stored by this object. All [ObservableList]s
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
     * Fills a given [ObservableList] of [Path] objects representing all of the files with the
     * given extension that were present in the folder within [_resourceFolder] denoted by
     * [pathFromResource].
     */
    private fun fillFileList(fileList: ObservableList<Path>, pathFromResource: String, extension: String) {
        val basePath: Path = Paths.get(_resourceFolder.toString() + File.separatorChar + pathFromResource)

        try {
            Files.newDirectoryStream(basePath, '*' + extension).use {
                for (p in it) {
                    val fname = p.baseFilename(extension)
                    if (fname.length <= PxPack.Head.FILENAME_MAX_LEN && !fname.contains(' ')) {
                        fileList.add(p)
                    }
                }
            }
        }
        catch (except: IOException) {
            throw IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept"), _executable), except)
        }
    }

    /**
     * Throws an [IllegalStateException] if [isInitialized] is false, otherwise returns normally
     *
     * @param retrievedProperty the name of the property attempting to be
     * retrieved. It will be put into the message of the [IllegalStateException]
     * that is thrown when [isInitialized] is false so that the message can be
     * more informative.
     */
    private fun checkInit(retrievedProperty: String) {
        if (!isInitialized) {
            throw IllegalStateException("GameData must be initialized before $retrievedProperty can be retrieved from it")
        }
    }
}