package io.fdeitylink.keroedit.util;

import java.util.logging.LogRecord;
import java.util.logging.FileHandler;
import java.util.logging.Level;

import java.io.IOException;

import io.fdeitylink.keroedit.Messages;
import javafx.scene.control.Alert;

//TODO: Specify log Levels?

public final class Logger {
    private Logger() {

    }

    public static void logMessage(final String message) {
        try {
            final FileHandler logFile = new FileHandler("error.log");
            logFile.publish(new LogRecord(Level.ALL, message));
            logFile.close();
        }
        catch (final IOException except) {

        }
    }

    public static void logThrowable(final Throwable t) {
        logThrowable("", t);
    }

    public static void logThrowable(final String message, final Throwable t) {
        final StringBuilder sBuilder = new StringBuilder(message);
        sBuilder.append('\n');
        sBuilder.append(t.getClass().getName());
        sBuilder.append(": ");
        sBuilder.append(t.getMessage());

        for (final StackTraceElement element : t.getStackTrace()) {
            sBuilder.append("\n\t");
            sBuilder.append(element);
        }
        final String finalMessage = sBuilder.toString();

        try {
            final FileHandler logFile = new FileHandler("error.log");
            logFile.publish(new LogRecord(Level.ALL, finalMessage));
            logFile.close();
        }
        catch (final IOException ex) {

        }
    }
}