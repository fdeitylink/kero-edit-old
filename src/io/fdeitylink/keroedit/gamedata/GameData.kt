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
     * Don't return a new ObservableList in every call to get()
     * Make use of the unused lists
     * Store the items in the localize folder in these lists?
     */

    private var _bgms: ObservableList<Path>? = null
    val bgms: ObservableList<Path>
        get() {
            checkInit("bgms")
            return FXCollections.unmodifiableObservableList(_bgms)
        }

    private var _maps: ObservableList<Path>? = null
    val maps: ObservableList<Path>
        get() {
            checkInit("maps")
            return FXCollections.unmodifiableObservableList(_maps)
        }

    private var _images: ObservableList<Path>? = null
    val images: ObservableList<Path>
        get() {
            checkInit("images")
            return FXCollections.unmodifiableObservableList(_images)
        }

    private var _sfx: ObservableList<Path>? = null
    val sfx: ObservableList<Path>
        get() {
            checkInit("sfx")
            return FXCollections.unmodifiableObservableList(_sfx)
        }

    private var _scripts: ObservableList<Path>? = null
    val scripts: ObservableList<Path>
        get() {
            checkInit("scripts")
            return FXCollections.unmodifiableObservableList(_scripts)
        }

    @Throws(IOException::class)
    fun init(executable: Path) {
        wipe()

        val exe = executable.toAbsolutePath()

        if (!exe.fileName.toString().endsWith(".exe")) {
            throw IllegalArgumentException("GameData must be initialized with an executable file ending in \".exe\" (exe: $exe)")
        }

        if (!Files.exists(exe)) {
            throw NoSuchFileException(exe.toString(), null, "Mod exe does not exist")
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
            _bgms = fileList(bgmFolder, bgmExtension)

            _maps = fileList(mapFolder, mapExtension)

            /*_maps.addListener(ListChangeListener<Path> { c: ListChangeListener.Change<out Path> ->
                val removed = c.removed
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
            _images = fileList(imageFolder, imageExtension)

            _sfx = fileList(sfxFolder, sfxExtension)

            _scripts = fileList(scriptFolder, scriptExtension)
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

        _bgms?.clear()
        _bgms = null

        _maps?.clear()
        _maps = null

        _images?.clear()
        _images = null

        _sfx?.clear()
        _sfx = null

        _scripts?.clear()
        _scripts = null
    }

    //TODO: Put a similar method into Utils.kt that returns a List?
    /**
     * Returns an [ObservableList] of [Path] objects representing all of the files with the
     * given extension that were present in the folder within [_resourceFolder] denoted by
     * [pathFromResource].
     */
    private fun fileList(pathFromResource: String, extension: String): ObservableList<Path> {
        val basePath: Path = Paths.get(_resourceFolder.toString() + File.separatorChar + pathFromResource)

        val list = FXCollections.observableArrayList(ArrayList<Path>())

        try {
            Files.newDirectoryStream(basePath, '*' + extension).use {
                for (p in it) {
                    val fname = p.baseFilename(extension)
                    if (fname.length <= PxPack.Head.FILENAME_MAX_LEN && !fname.contains(' ')) {
                        list.add(p)
                    }
                }
            }
        }
        catch (except: IOException) {
            throw IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept"), _executable), except)
        }

        return list
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

    /**
     * An enum class representing the different types of Kero Blaster mods
     * that can exist. Each constant within the class represents a different
     * game based on the Kero Blaster engine. Although each is mostly the same
     * in terms of the engine and capabilities, there are some discrepancies
     * that must be dealt with.
     */
    enum class ModType {
        /**
         * A mod based on the Pink Hour game
         */
        PINK_HOUR,

        /**
         * A mod based on the Pink Heaven game
         */
        PINK_HEAVEN,

        /**
         * A mod based on the Kero Blaster game
         */
        KERO_BLASTER
    }
}