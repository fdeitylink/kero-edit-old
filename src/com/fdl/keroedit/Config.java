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

    public static String lastExeLoc;
    public static int mapZoom;
    public static int tilesetZoom;
    public static Color tilesetBgColor;

    private Config() {

    }

    public static void loadPreferences() {
        final String lastThreadName = Thread.currentThread().getName();

        Thread.currentThread().setName(MessageFormat.format(Messages.getString("KeroEdit.MAIN_LOGIC_THREAD_NAME"),
                                                            Messages.getString("Config.LOAD_PREFERENCES_THREAD_NAME")));

        lastExeLoc = prefs.get(Messages.getString("Config.LAST_EXE_LOC"), System.getProperty("user.dir"));
        mapZoom = prefs.getInt(Messages.getString("Config.MAP_ZOOM"), 1);
        tilesetZoom = prefs.getInt(Messages.getString("Config.TILESET_ZOOM"), 1);
        tilesetBgColor = Color.web(prefs.get(Messages.getString("Config.TILESET_BG_COLOR"),
                                             JavaFXUtil.colorToString(Color.MAGENTA)));

        Thread.currentThread().setName(lastThreadName);
    }

    public static void savePreferences() {
        final String lastThreadName = Thread.currentThread().getName();

        Thread.currentThread().setName(MessageFormat.format(Messages.getString("KeroEdit.MAIN_LOGIC_THREAD_NAME"),
                                                            Messages.getString("Config.SAVE_PREFERENCES_THREAD_NAME")));

        try {
            prefs.put(Messages.getString("Config.LAST_EXE_LOC"), lastExeLoc);
            prefs.putInt(Messages.getString("Config.MAP_ZOOM"), mapZoom);
            prefs.putInt(Messages.getString("Config.TILESET_ZOOM"), tilesetZoom);
            prefs.put(Messages.getString("Config.TILESET_BG_COLOR"), JavaFXUtil.colorToString(tilesetBgColor));

            prefs.flush();
        }
        catch (final BackingStoreException except) {
            Logger.logException(MessageFormat.format(Messages.getString("Config.SAVE_FAILURE"),
                                                     except.getMessage()), except);
            //TODO: Show error alert?
        }

        Thread.currentThread().setName(lastThreadName);
    }
}