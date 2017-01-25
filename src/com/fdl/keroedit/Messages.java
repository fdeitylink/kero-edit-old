package com.fdl.keroedit;

import com.fdl.keroedit.util.Logger;

import java.util.ResourceBundle;
import java.util.MissingResourceException;

public class Messages {
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("com.fdl.keroedit.messages");

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
