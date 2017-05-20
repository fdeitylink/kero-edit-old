/* TODO:
* Override set() for all member properties
*  - Check integer flags for validity
* Make map-editing related member properties JavaFX properties (Kotlin properties?) that can be bound to?
*/

package io.fdeitylink.keroedit

import java.util.EnumSet

import java.util.prefs.Preferences
import java.util.prefs.BackingStoreException

import kotlin.properties.Delegates

import kotlin.jvm.javaClass

import javafx.scene.paint.Color

import io.fdeitylink.util.encoded
import io.fdeitylink.util.decode

import io.fdeitylink.util.Logger

import io.fdeitylink.util.fx.FXUtil

import io.fdeitylink.keroedit.mapedit.MapEditTab

internal object Config {
    private val prefs = Preferences.userNodeForPackage(javaClass)

    var licenseRead by Delegates.notNull<Boolean>()

    lateinit var notepadText: String

    var lastExeLoc by Delegates.notNull<String>()

    var mapZoom by Delegates.notNull<Double>()
    var tilesetZoom by Delegates.notNull<Double>()
    lateinit var tilesetBgColor: Color

    lateinit var displayedLayers: EnumSet<MapEditTab.LayerFlag>

    var selectedLayer by Delegates.notNull<Int>()

    lateinit var drawMode: MapEditTab.DrawMode

    lateinit var viewSettings: EnumSet<MapEditTab.ViewFlag>

    lateinit var editMode: MapEditTab.EditMode

    var tilesetStageShowing by Delegates.notNull<Boolean>()

    fun loadPrefs() {
        licenseRead = prefs.getBoolean("LICENSE_READ", false)

        notepadText = prefs.get("NOTEPAD_TEXT", Messages.getString("Config.NOTEPAD_TEXT_DEFAULT"))

        lastExeLoc = prefs.get("LAST_EXECUTABLE_LOCATION", System.getProperty("user.dir"))

        mapZoom = prefs.getDouble("MAP_ZOOM", 1.0)
        tilesetZoom = prefs.getDouble("TILESET_ZOOM", 1.0)

        val colStr: String? = prefs.get("TILESET_BIG_COLOR", null)
        tilesetBgColor = if (null == colStr) Color.MAGENTA else Color.web(colStr)

        //0b0000_0111 -> defaults to all displayed
        displayedLayers = prefs.getLong("DISPLAYED_LAYERS", 0b0000_0111).decode(MapEditTab.LayerFlag::class)
        selectedLayer = prefs.getInt("SELECTED_LAYER", 0)

        drawMode = MapEditTab.DrawMode.values()[prefs.getInt("DRAW_MODE", 0)]

        //TODO: Default to entity sprites and boxes displayed
        //0b0000_0000 -> defaults as nothing extra displayed
        viewSettings = prefs.getLong("VIEW_SETTINGS", 0b0000_0000).decode(MapEditTab.ViewFlag::class)

        editMode = MapEditTab.EditMode.values()[prefs.getInt("EDIT_MODE", 0)]

        tilesetStageShowing = prefs.getBoolean("TILESET_STAGE_SHOWING", false)
    }

    fun savePrefs() {
        try {
            prefs.putBoolean("LICENSE_READ", licenseRead)
            prefs.put("LAST_EXECUTABLE_LOCATION", lastExeLoc)
            prefs.put("NOTEPAD_TEXT", notepadText)

            prefs.putDouble("MAP_ZOOM", mapZoom)
            prefs.putDouble("TILESET_ZOOM", tilesetZoom)
            prefs.put("TILESET_BG_COLOR", FXUtil.colorToString(tilesetBgColor))

            prefs.putLong("DISPLAYED_LAYERS", displayedLayers.encoded())
            prefs.putInt("SELECTED_LAYER", selectedLayer)

            prefs.putInt("DRAW_MODE", drawMode.ordinal)

            prefs.putLong("VIEW_SETTINGS", viewSettings.encoded())

            prefs.putInt("EDIT_MODE", editMode.ordinal)

            prefs.putBoolean("TILESET_STAGE_SHOWING", tilesetStageShowing)

            prefs.flush()
        }
        catch (except: BackingStoreException) {
            Logger.logThrowable("Failed to save preferences", except)
        }
    }
}