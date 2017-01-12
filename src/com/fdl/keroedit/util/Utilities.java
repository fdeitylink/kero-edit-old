package com.fdl.keroedit.util;

import javafx.scene.control.TabPane;

import javafx.scene.control.Alert;

import javafx.event.EventHandler;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.jetbrains.annotations.Contract;

public class Utilities {
    public static void addControlWClose(TabPane tabPane) {
        tabPane.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(final KeyEvent event) {
                if (tabPane.isFocused() && event.getCode().equals(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN).getCode())) {
                    final int selectedTabIndex = tabPane.getSelectionModel().getSelectedIndex();
                    tabPane.getTabs().remove(selectedTabIndex); //Seems to throw out of bounds except if you go to fast, though it doesn't halt program
                }
            }
        });
    }

    public static void createErrorAlert(String title, String headerText, String message) {
        System.err.println(message);
        createAlert(Alert.AlertType.ERROR, title, headerText, message, true);
    }

    public static void createInformationAlert(String title, String headerText, String message) {
        createAlert(Alert.AlertType.INFORMATION, title, headerText, message, false);
    }

    private static void createAlert(Alert.AlertType type, String title, String headerText, String message, boolean wait) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);
        if (wait) {
            alert.showAndWait();
        }
        else {
            alert.show();
        }
    }

    /**
     * Provides the index of a point in an rectangular grid given its x and y coordinates, if the index
     * increases by 1 from left to right and when it moves from the end of one line to the start of the next
     *
     * @param x The x coordinate of the index
     * @param y The y coordinate of the index
     * @param width The width of the grid (length of the x-axis)
     *
     * @return The index to use
     */
    @Contract(pure = true)
    public static long XYToIndex(long x, long y, long width) {
        return (width * y) + x;
    }

    /**
     * Provides the x and y coordinates of an index in a rectangular grid
     *
     * @param width The width of the grid (length of the x-axis)
     * @param index The index in the grid if it increases by 1 from left to right and when it moves from
     *              the end of one line to the start of the next
     *
     * @return A {@code CoordinatePair} object with the x and y coordinates of the index
     */
    @Contract("_, _ -> !null")
    public static CoordinatePair indexToXY(long width, long index) {
        return new Utilities().new CoordinatePair(index % width, index / width);
    }

    /**
     * Stores the x and y coordinates of a point in a grid
     */
    public class CoordinatePair {
        public long x, y;
        public CoordinatePair() {
            x = 0;
            y = 0;
        }
        public CoordinatePair(long x, long y) {
            this.x = x;
            this.y = y;
        }
    }
}