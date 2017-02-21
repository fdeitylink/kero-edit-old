package com.fdl.keroedit;

import java.text.MessageFormat;

import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;

import javafx.scene.paint.Color;

import com.fdl.keroedit.util.Logger;

import com.fdl.keroedit.util.JavaFXUtil;

/**
 * Stores configuration options
 */
public class Config {
    private static final Preferences prefs = Preferences.userNodeForPackage(Config.class);

    public static boolean licenseRead;
    public static String lastExeLoc;
    public static int mapZoom;
    public static int tilesetZoom;
    public static Color tilesetBgColor;
    public static String notepadText;

    private Config() {

    }

    public static void loadPreferences() {
        licenseRead = prefs.getBoolean(Messages.getString("Config.LICENSE_READ"), false);
        lastExeLoc = prefs.get(Messages.getString("Config.LAST_EXE_LOC"), System.getProperty("user.dir"));
        mapZoom = prefs.getInt(Messages.getString("Config.MAP_ZOOM"), 2);
        tilesetZoom = prefs.getInt(Messages.getString("Config.TILESET_ZOOM"), 2);
        tilesetBgColor = Color.web(prefs.get(Messages.getString("Config.TILESET_BG_COLOR"),
                                             JavaFXUtil.colorToString(Color.MAGENTA)));
        notepadText = prefs.get(Messages.getString("Config.NOTEPAD_TEXT"),
                                Messages.getString("Config.NOTEPAD_TEXT_DEFAULT"));
    }

    public static void savePreferences() {
        try {
            prefs.putBoolean(Messages.getString("Config.LICENSE_READ"), licenseRead);
            prefs.put(Messages.getString("Config.LAST_EXE_LOC"), lastExeLoc);
            prefs.putInt(Messages.getString("Config.MAP_ZOOM"), mapZoom);
            prefs.putInt(Messages.getString("Config.TILESET_ZOOM"), tilesetZoom);
            prefs.put(Messages.getString("Config.TILESET_BG_COLOR"), JavaFXUtil.colorToString(tilesetBgColor));
            prefs.put(Messages.getString("Config.NOTEPAD_TEXT"), notepadText);

            prefs.flush();
        }
        catch (final BackingStoreException except) {
            Logger.logException(MessageFormat.format(Messages.getString("Config.SAVE_FAILURE"),
                                                     except.getMessage()), except);
            //TODO: Show error alert?
        }
    }
}