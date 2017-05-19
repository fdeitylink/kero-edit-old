package io.fdeitylink.util

import java.util.logging.LogRecord
import java.util.logging.FileHandler
import java.util.logging.Level

import java.io.IOException

//TODO: Allow specifying Log Levels?

object Logger {
    fun logMessage(message: String) {
        try {
            val handle = FileHandler("error.log")
            handle.publish(LogRecord(Level.ALL, message))
            handle.close()
        }
        catch (except: IOException) {
            System.err.println(message)
        }
    }

    fun logThrowable(message: String = "", t: Throwable) {
        val builder = StringBuilder(message).append('\n')
        builder.append("${t.javaClass.name}: ${t.message}")

        val stackTrace = t.stackTrace
        for (element in stackTrace) {
            builder.append("\n\t$element")
        }

        val finalMessage = builder.toString()

        try {
            val handle = FileHandler("error.log")
            handle.publish(LogRecord(Level.ALL, finalMessage))
            handle.close()
        }
        catch (except: IOException) {
            System.err.println(finalMessage)
        }
    }
}