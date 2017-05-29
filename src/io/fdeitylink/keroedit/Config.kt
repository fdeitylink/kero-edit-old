/*
 * TODO:
 * Make map-editing related member properties JavaFX properties (Kotlin properties?) that can be bound to?
 */

package io.fdeitylink.keroedit

import java.util.EnumSet

import java.util.prefs.Preferences
import java.util.prefs.BackingStoreException

import java.nio.file.Path
import java.nio.file.Paths

import java.nio.file.InvalidPathException

import kotlin.properties.Delegates

import kotlin.jvm.javaClass

import javafx.scene.paint.Color

import io.fdeitylink.util.encoded
import io.fdeitylink.util.decoded

import io.fdeitylink.util.Logger

import io.fdeitylink.util.fx.FXUtil

import io.fdeitylink.keroedit.mapedit.MapEditTab

internal object Config {
    var licenseRead by Delegates.notNull<Boolean>()

    lateinit var notepadText: String

    var lastExeLoc by Delegates.notNull<Path>()

    var mapZoom by Delegates.notNull<Double>()

    var tilesetZoom by Delegates.notNull<Double>()
    lateinit var tilesetBgColor: Color
    lateinit var displayedLayers: EnumSet<MapEditTab.Layer>

    lateinit var selectedLayer: MapEditTab.Layer

    lateinit var drawMode: MapEditTab.DrawMode

    lateinit var viewSettings: EnumSet<MapEditTab.ViewOption>

    lateinit var editMode: MapEditTab.EditMode

    var tilesetStageShowing by Delegates.notNull<Boolean>()

    private val prefs = Preferences.userNodeForPackage(javaClass)

    private const val LICENSE_READ = "LICENSE_READ"
    private const val NOTEPAD_TEXT = "NOTEPAD_TEXT"
    private const val LAST_EXECUTABLE_LOCATION = "LAST_EXECUTABLE_LOCATION"
    private const val MAP_ZOOM = "MAP_ZOOM"
    private const val TILESET_ZOOM = "TILESET_ZOOM"
    private const val TILESET_BG_COLOR = "TILESET_BG_COLOR"
    private const val DISPLAYED_LAYERS = "DISPLAYED_LAYERS"
    private const val SELECTED_LAYER = "SELECTED_LAYER"
    private const val DRAW_MODE = "DRAW_MODE"
    private const val VIEW_SETTINGS = "VIEW_SETTINGS"
    private const val EDIT_MODE = "EDIT_MODE"
    private const val TILESET_STAGE_SHOWING = "TILESET_STAGE_SHOWING"

    fun load() {
        licenseRead = prefs.getBoolean(LICENSE_READ, false)

        notepadText = prefs.get(NOTEPAD_TEXT, Messages.getString("Config.NOTEPAD_TEXT_DEFAULT"))

        try {
            lastExeLoc = Paths.get(prefs.get(LAST_EXECUTABLE_LOCATION, System.getProperty("user.dir")))
        }
        catch (except: InvalidPathException) {
            lastExeLoc = Paths.get(System.getProperty("user.dir"))
        }

        mapZoom = prefs.getDouble(MAP_ZOOM, 1.0)
        tilesetZoom = prefs.getDouble(TILESET_ZOOM, 1.0)

        val colStr: String? = prefs.get(TILESET_BG_COLOR, null)
        tilesetBgColor = if (null == colStr) Color.MAGENTA else Color.web(colStr)

        //0b0000_0111 -> defaults to all displayed
        displayedLayers = prefs.getLong(DISPLAYED_LAYERS, 0b0000_0111).decoded(MapEditTab.Layer::class)
        selectedLayer = MapEditTab.Layer.values()[prefs.getInt(SELECTED_LAYER, 0)]

        drawMode = MapEditTab.DrawMode.values()[prefs.getInt(DRAW_MODE, 0)]

        //TODO: Also show entity names by default when they are enabled
        //0b0000_1100 -> defaults as entity boxes and sprites shown
        viewSettings = prefs.getLong(VIEW_SETTINGS, 0b0000_1100).decoded(MapEditTab.ViewOption::class)

        editMode = MapEditTab.EditMode.values()[prefs.getInt(EDIT_MODE, 0)]

        tilesetStageShowing = prefs.getBoolean(TILESET_STAGE_SHOWING, false)
    }

    fun save() {
        try {
            prefs.putBoolean(LICENSE_READ, licenseRead)
            prefs.put(LAST_EXECUTABLE_LOCATION, lastExeLoc.toString())
            prefs.put(NOTEPAD_TEXT, notepadText)

            prefs.putDouble(MAP_ZOOM, mapZoom)
            prefs.putDouble(TILESET_ZOOM, tilesetZoom)
            prefs.put(TILESET_BG_COLOR, FXUtil.colorToString(tilesetBgColor))

            prefs.putLong(DISPLAYED_LAYERS, displayedLayers.encoded())
            prefs.putInt(SELECTED_LAYER, selectedLayer.ordinal)

            prefs.putInt(DRAW_MODE, drawMode.ordinal)

            prefs.putLong(VIEW_SETTINGS, viewSettings.encoded())

            prefs.putInt(EDIT_MODE, editMode.ordinal)

            prefs.putBoolean(TILESET_STAGE_SHOWING, tilesetStageShowing)

            prefs.flush()
        }
        catch (except: BackingStoreException) {
            Logger.logThrowable("Failed to save preferences", except)
        }
    }
}
