package io.fdeitylink.keroedit

import java.util.ResourceBundle
import java.util.MissingResourceException

import io.fdeitylink.util.Logger

object Messages {
    private val resourceBundle = ResourceBundle.getBundle("io.fdeitylink.keroedit.messages")

    /**
     * Returns a {@code String} that corresponds to a key in the {@code messages.properties}
     * resource bundle.
     *
     * @param key The key corresponding to the {@code String} to retrieve
     *
     * @return The {@code String} corresponding to {@code key}
     */
    fun getString(key: String): String {
        try {
            return resourceBundle.getString(key)
        }
        catch (except: MissingResourceException) {
            Logger.logThrowable("Missing string resource $key", except)
            return "Missing resource: $key"
        }
    }
}