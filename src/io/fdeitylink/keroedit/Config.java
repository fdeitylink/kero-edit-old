package io.fdeitylink.keroedit;

import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;

import java.util.EnumSet;

import javafx.scene.paint.Color;

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.util.SafeEnum;

import io.fdeitylink.keroedit.util.fx.FXUtil;

import io.fdeitylink.keroedit.mapedit.MapEditTab;

/**
 * Stores configuration options
 */
public final class Config {
    //TODO: Setters and value checking?
    private static final Preferences prefs = Preferences.userNodeForPackage(Config.class);

    static boolean licenseRead;
    static String lastExeLoc;
    static String notepadText;

    //TODO: Make all of the following Properties that can be bound to?
    public static int mapZoom;
    public static int tilesetZoom;
    public static Color tilesetBgColor;

    public static EnumSet <MapEditTab.LayerFlag> displayedLayers;
    public static int selectedLayer;

    public static MapEditTab.DrawMode drawMode;

    public static EnumSet <MapEditTab.ViewFlag> viewSettings;

    public static MapEditTab.EditMode editMode;

    public static boolean tilesetStageShowing;

    private Config() {

    }

    static void loadPrefs() {
        licenseRead = prefs.getBoolean("LICENSE_READ", false);
        lastExeLoc = prefs.get("LAST_EXECUTABLE_LOCATION", System.getProperty("user.dir"));
        notepadText = prefs.get("NOTEPAD_TEXT", Messages.getString("Config.NOTEPAD_TEXT_DEFAULT"));

        mapZoom = prefs.getInt("MAP_ZOOM", 2);
        tilesetZoom = prefs.getInt("TILESET_ZOOM", 2);

        final String colStr = prefs.get("TILESET_BG_COLOR", null);
        tilesetBgColor = null == colStr ? Color.MAGENTA : Color.web(colStr);

        //TODO: Should check int flags for validity if possible

        //0b0000_0111 -> initializes as all layers displayed
        displayedLayers = decode(prefs.getInt("DISPLAYED_LAYERS", 0b0000_0111), MapEditTab.LayerFlag.class);
        selectedLayer = prefs.getInt("SELECTED_LAYER", 0);

        drawMode = MapEditTab.DrawMode.values()[prefs.getInt("DRAW_MODE", 0)];

        //0b0000_0000 -> initializes as nothing on
        viewSettings = decode(prefs.getInt("VIEW_SETTINGS", 0b0000_0000), MapEditTab.ViewFlag.class);

        editMode = MapEditTab.EditMode.values()[prefs.getInt("EDIT_MODE", 0)];

        tilesetStageShowing = prefs.getBoolean("TILESET_STAGE_SHOWING", false);
    }

    static void savePrefs() {
        try {
            prefs.putBoolean("LICENSE_READ", licenseRead);
            prefs.put("LAST_EXECUTABLE_LOCATION", lastExeLoc);
            prefs.put("NOTEPAD_TEXT", notepadText);

            prefs.putInt("MAP_ZOOM", mapZoom);
            prefs.putInt("TILESET_ZOOM", tilesetZoom);
            prefs.put("TILESET_BG_COLOR", FXUtil.colorToString(tilesetBgColor));

            prefs.putInt("DISPLAYED_LAYERS", encode(displayedLayers));
            prefs.putInt("SELECTED_LAYER", selectedLayer);

            prefs.putInt("DRAW_MODE", drawMode.ordinal());

            prefs.putInt("VIEW_SETTINGS", encode(viewSettings));

            prefs.putInt("EDIT_MODE", editMode.ordinal());

            prefs.putBoolean("TILESET_STAGE_SHOWING", tilesetStageShowing);

            prefs.flush();
        }
        catch (final BackingStoreException except) {
            Logger.logThrowable("Failed to save preferences", except);
        }
    }

    private static <E extends Enum <E> & SafeEnum <E>> int encode(final EnumSet <E> set) {
        //Assumes only 32 bits (uses int)
        //http://stackoverflow.com/a/2199486

        int flags = 0;
        for (final E val : set) {
            flags |= 1 << val.ordinal();
            //flags |= (1 << val.ordinalMap(enumClass).get(val));
        }

        return flags;
    }

    private static <E extends Enum <E> & SafeEnum <E>> EnumSet <E> decode(final int encoded, final Class <E> enumClass) {
        //Assumes only 32 bits (uses int)
        //http://stackoverflow.com/a/2199486

        final EnumSet <E> set = EnumSet.noneOf(enumClass);
        int ordinal = 0;

        final E[] constants = enumClass.getEnumConstants();
        //Repeatedly left-shift i to go through every bit
        for (int i = 1; i != 0 && ordinal < enumClass.getEnumConstants().length; i <<= 1) {
            if (0 != (i & encoded)) {
                set.add(constants[ordinal]);
            }
            ordinal++;
        }

        return set;
    }
}