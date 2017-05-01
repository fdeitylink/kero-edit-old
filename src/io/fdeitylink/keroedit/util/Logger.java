package io.fdeitylink.keroedit.util;

import java.util.logging.LogRecord;
import java.util.logging.FileHandler;
import java.util.logging.Level;

import java.io.IOException;

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
            System.err.println("Error: " + message);
        }
    }

    public static void logThrowable(final Throwable t) {
        logThrowable("", t);
    }

    public static void logThrowable(final String message, final Throwable t) {
        final StringBuilder sBuilder = new StringBuilder(message).append('\n');
        sBuilder.append(t.getClass().getName()).append(": ").append(t.getMessage());

        for (final StackTraceElement element : t.getStackTrace()) {
            sBuilder.append("\n\t").append(element);
        }
        final String finalMessage = sBuilder.toString();

        try {
            final FileHandler logFile = new FileHandler("error.log");
            logFile.publish(new LogRecord(Level.ALL, finalMessage));
            logFile.close();
        }
        catch (final IOException ex) {
            System.err.println(finalMessage);
        }
    }
}