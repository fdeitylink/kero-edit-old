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
    private static final Preferences prefs = Preferences.userNodeForPackage(Config.class);
    public static int mapZoom;
    public static int tilesetZoom;
    public static Color tilesetBgColor;
    public static int displayedLayers;
    static boolean licenseRead;
    static String lastExeLoc;
    static String notepadText;

    private Config() {

    }

    static void loadPrefs() {
        licenseRead = prefs.getBoolean(Messages.getString("Config.LICENSE_READ"), false);
        lastExeLoc = prefs.get(Messages.getString("Config.LAST_EXE_LOC"), System.getProperty("user.dir"));
        notepadText = prefs.get(Messages.getString("Config.NOTEPAD_TEXT"),
                                Messages.getString("Config.NOTEPAD_TEXT_DEFAULT"));

        mapZoom = prefs.getInt(Messages.getString("Config.MAP_ZOOM"), 2);
        tilesetZoom = prefs.getInt(Messages.getString("Config.TILESET_ZOOM"), 2);
        tilesetBgColor = Color.web(prefs.get(Messages.getString("Config.TILESET_BG_COLOR"),
                                             FXUtil.colorToString(Color.MAGENTA)));

        //inits as all layers displayed
        displayedLayers = prefs.getInt(Messages.getString("Config.DISPLAYED_LAYERS"), 0b111);
    }

    static void savePrefs() {
        try {
            prefs.putBoolean(Messages.getString("Config.LICENSE_READ"), licenseRead);
            prefs.put(Messages.getString("Config.LAST_EXE_LOC"), lastExeLoc);
            prefs.put(Messages.getString("Config.NOTEPAD_TEXT"), notepadText);

            prefs.putInt(Messages.getString("Config.MAP_ZOOM"), mapZoom);
            prefs.putInt(Messages.getString("Config.TILESET_ZOOM"), tilesetZoom);
            prefs.put(Messages.getString("Config.TILESET_BG_COLOR"), FXUtil.colorToString(tilesetBgColor));

            prefs.putInt(Messages.getString("Config.DISPLAYED_LAYERS"), displayedLayers);

            prefs.flush();
        }
        catch (final BackingStoreException except) {
            Logger.logException(Messages.getString("Config.SAVE_FAILURE"), except);
        }
    }
}