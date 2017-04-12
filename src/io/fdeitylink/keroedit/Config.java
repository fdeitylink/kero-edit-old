package io.fdeitylink.keroedit;

import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;

import javafx.scene.paint.Color;

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.util.FXUtil;

/**
 * Stores configuration options
 */
public final class Config {
    //TODO: Setters and value checking?
    private static final Preferences prefs = Preferences.userNodeForPackage(Config.class);

    static boolean licenseRead;
    static String lastExeLoc;
    static String notepadText;

    public static int mapZoom;
    public static int tilesetZoom;
    public static Color tilesetBgColor;

    public static int displayedLayers;
    public static int selectedLayer;

    public static KeroEdit.DrawMode drawMode;

    private Config() {

    }

    static void loadPrefs() {
        licenseRead = prefs.getBoolean("LICENSE_READ", false);
        lastExeLoc = prefs.get("LAST_EXECUTABLE_LOCATION", System.getProperty("user.dir"));
        notepadText = prefs.get("NOTEPAD_TEXT", Messages.getString("Config.NOTEPAD_TEXT_DEFAULT"));

        mapZoom = prefs.getInt("MAP_ZOOM", 2);
        tilesetZoom = prefs.getInt("TILESET_ZOOM", 2);
        tilesetBgColor = Color.web(prefs.get("TILESET_BG_COLOR", FXUtil.colorToString(Color.MAGENTA)));

        //0b111 -> inits as all layers displayed
        displayedLayers = prefs.getInt("DISPLAYED_LAYERS", 0b111);
        selectedLayer = prefs.getInt("SELECTED_LAYER", 0);

        final int drawModeIndex = prefs.getInt("DRAW_MODE", 0);
        drawMode = KeroEdit.DrawMode.values()[drawModeIndex];
    }

    static void savePrefs() {
        try {
            prefs.putBoolean("LICENSE_READ", licenseRead);
            prefs.put("LAST_EXECUTABLE_LOCATION", lastExeLoc);
            prefs.put("NOTEPAD_TEXT", notepadText);

            prefs.putInt("MAP_ZOOM", mapZoom);
            prefs.putInt("TILESET_ZOOM", tilesetZoom);
            prefs.put("TILESET_BG_COLOR", FXUtil.colorToString(tilesetBgColor));

            prefs.putInt("DISPLAYED_LAYERS", displayedLayers);
            prefs.putInt("SELECTED_LAYER", selectedLayer);

            final int drawModeIndex = KeroEdit.DrawMode.arrIndexEnumMap.get(drawMode);
            prefs.putInt("DRAW_MODE", drawModeIndex);

            prefs.flush();
        }
        catch (final BackingStoreException except) {
            Logger.logThrowable("Failed to save preferences", except);
        }
    }
}