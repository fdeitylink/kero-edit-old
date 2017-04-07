package io.fdeitylink.keroedit;

import java.util.ResourceBundle;
import java.util.MissingResourceException;

import io.fdeitylink.keroedit.util.Logger;

public final class Messages {
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("io.fdeitylink.keroedit.messages");

    private Messages() {

    }

    /**
     * Returns a {@code String} that corresponds to a key in the {@code messages.properties}
     * resource bundle.
     *
     * @param key The key corresponding to the {@code String} to retrieve
     *
     * @return The {@code String} corresponding to {@code key}
     */
    public static String getString(final String key) {
        try {
            return resourceBundle.getString(key);
        }
        catch (final MissingResourceException except) {
            Logger.logThrowable("Missing string resource: " + key, except);
            return "Missing resource: " + key;
        }
    }
}