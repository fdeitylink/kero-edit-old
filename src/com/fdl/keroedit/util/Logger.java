package com.fdl.keroedit.util;

import java.util.logging.LogRecord;
import java.util.logging.FileHandler;
import java.util.logging.Level;

import java.io.IOException;

import java.util.Date;
import java.text.SimpleDateFormat;

import com.fdl.keroedit.Messages;
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
            JavaFXUtil.createAlertWithTextBox(Alert.AlertType.ERROR, Messages.getString("Logger.Alert.TITLE"),
                                              null, Messages.getString("Logger.Alert.MESSAGE"),
                                              currentTime + ": " + message, false).showAndWait();
        }
    }

    public static void logException(final String additionalMessage, final Exception except) {
        final String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        String message = currentTime + ": " + additionalMessage + except.getMessage();
        for (final StackTraceElement element : except.getStackTrace()) {
            message += "\n\t" + element;
        }

        try {
            logFile = new FileHandler("error.log");
            logFile.publish(new LogRecord(Level.ALL, message));
            logFile.close();
        }
        catch (final IOException ioExcept) {
            JavaFXUtil.createAlertWithTextBox(Alert.AlertType.ERROR, Messages.getString("Logger.Alert.TITLE"),
                                              null, Messages.getString("Logger.Alert.MESSAGE"),
                                              message, false).showAndWait();
        }
    }
}