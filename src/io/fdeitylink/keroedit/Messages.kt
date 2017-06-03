package io.fdeitylink.keroedit

import java.util.ResourceBundle
import java.util.MissingResourceException

import io.fdeitylink.util.Logger

object Messages {
    private val resourceBundle = ResourceBundle.getBundle("io.fdeitylink.keroedit.messages")

    /**
     * Returns a [String] that corresponds to a key in the messages.properties
     * resource bundle in the [io.fdeitylink.keroedit] package.
     *
     * @param key the key corresponding to the [String] to retrieve
     *
     * @return the [String] corresponding to [key], or [key] if there is no
     * mapping for it in the resource bundle
     */
    operator fun get(key: String): String {
        try {
            return resourceBundle.getString(key)
        }
        catch (except: MissingResourceException) {
            Logger.logMessage("Missing string resource $key")
            return key
        }
    }
}