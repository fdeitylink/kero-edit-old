/*
 * TODO:
 * Allow specifying Log Levels?
 * Remove @JvmOverloads annotation on logThrowable when everything is converted to Kotlin
 * Am I properly appending log records rather than overwriting?
 */
package io.fdeitylink.util

import java.util.logging.LogRecord
import java.util.logging.FileHandler
import java.util.logging.Level

import java.io.IOException

object Logger {
    fun logMessage(message: String, logFile: String = "error.log") {
        try {
            val handle = FileHandler(logFile)
            handle.publish(LogRecord(Level.ALL, message))
            handle.close()
        }
        catch (except: IOException) {
            System.err.println(message)
        }
    }

    @JvmOverloads
    fun logThrowable(message: String = "", t: Throwable, logFile: String = "error.log") {
        val builder = StringBuilder(message).append('\n')
        builder.append("${t.javaClass.name}: ${t.message}")

        val stackTrace = t.stackTrace
        for (element in stackTrace) {
            builder.append("\n\t$element")
        }

        val finalMessage = builder.toString()

        try {
            val handle = FileHandler(logFile)
            handle.publish(LogRecord(Level.ALL, finalMessage))
            handle.close()
        }
        catch (except: IOException) {
            System.err.println(finalMessage)
        }
    }
}