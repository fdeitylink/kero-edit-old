package io.fdeitylink.keroedit;

import java.util.ResourceBundle;
import java.util.MissingResourceException;

import io.fdeitylink.keroedit.util.Logger;

public final class Messages {
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("io.fdeitylink.keroedit.messages");

    private Messages() {

    }

    public static String getString(String key) {
        try {
            return resourceBundle.getString(key);
        }
        catch (final MissingResourceException except) {
            Logger.logException("Missing string resource: " + key, except);
            return "Missing resource: " + key;
        }
    }
}