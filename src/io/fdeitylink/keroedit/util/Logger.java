package io.fdeitylink.keroedit.util;

import java.util.logging.LogRecord;
import java.util.logging.FileHandler;
import java.util.logging.Level;

import java.io.IOException;

import java.util.Date;
import java.text.SimpleDateFormat;

import io.fdeitylink.keroedit.Messages;
import javafx.scene.control.Alert;

//TODO: Specify log Levels?

public class Logger {
    private static FileHandler logFile;

    private Logger() {

    }

    public static void logMessage(final String message) {
        final String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try {
            logFile = new FileHandler("error.log");
            logFile.publish(new LogRecord(Level.ALL, currentTime + ": " + message));
            logFile.close();
        }
        catch (final IOException except) {
            JavaFXUtil.createTextboxAlert(Alert.AlertType.ERROR, Messages.getString("Logger.Alert.TITLE"),
                                          null, Messages.getString("Logger.Alert.MESSAGE"),
                                              currentTime + ": " + message, false).showAndWait();
        }
    }

    public static void logException(final String message, final Exception except) {
        final String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        final StringBuilder sBuilder = new StringBuilder(currentTime);
        sBuilder.append(": ");
        sBuilder.append(message);
        sBuilder.append(except.getMessage());
        for (final StackTraceElement element : except.getStackTrace()) {
            sBuilder.append("\n\t");
            sBuilder.append(element);
        }
        final String finalMessage = sBuilder.toString();

        try {
            logFile = new FileHandler("error.log");
            logFile.publish(new LogRecord(Level.ALL, finalMessage));
            logFile.close();
        }
        catch (final IOException ioExcept) {
            JavaFXUtil.createTextboxAlert(Alert.AlertType.ERROR, Messages.getString("Logger.Alert.TITLE"),
                                          null, Messages.getString("Logger.Alert.MESSAGE"),
                                          finalMessage, false).showAndWait();
        }
    }
}