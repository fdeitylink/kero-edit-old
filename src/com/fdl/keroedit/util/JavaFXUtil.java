package com.fdl.keroedit.util;

import javafx.scene.paint.Color;

import javafx.scene.control.Dialog;
import javafx.util.Pair;

import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;

import javafx.scene.control.TextField;

import javafx.scene.layout.GridPane;

import javafx.application.Platform;

import javafx.util.Callback;

import javafx.scene.layout.Priority;

import javafx.scene.control.Alert;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public class JavaFXUtil {
    private JavaFXUtil() {

    }

    public static String colorToString(final Color color) {
        return String.format("0x%02X%02X%02X",
                             (int)(color.getRed() * 255),
                             (int)(color.getGreen() * 255),
                             (int)(color.getBlue() * 255));
    }

    /**
     * Scales a given image by a given scale factor
     *
     * @param src   The image to scale
     * @param scale The scale factor to scale the image by
     *
     * @return The result of scaling the given image by the given scale factor
     */
    public static Image scaleImage(final Image src, final int scale) {
        final int srcWidth = (int)src.getWidth();
        final int srcHeight = (int)src.getHeight();

        if (0 == srcWidth || 0 == srcHeight) {
            return null;
        }

        final WritableImage dest = new WritableImage(srcWidth * scale, srcHeight * scale);

        final PixelReader srcReader = src.getPixelReader();
        final PixelWriter destWriter = dest.getPixelWriter();

        for (int srcY = 0; srcY < srcHeight; ++srcY) {
            for (int srcX = 0; srcX < srcWidth; ++srcX) {
                final int pix = srcReader.getArgb(srcX, srcY);

                for (int dy = 0; dy < scale; ++dy) {
                    for (int dx = 0; dx < scale; ++dx) {
                        destWriter.setArgb((srcX * scale) + dx, (srcY * scale) + dy, pix);
                    }
                }
            }
        }

        return dest;
    }

    /**
     * Binds Ctrl + W to closing the current {@code Tab} and Ctrl + Shift + W to closing all the {@code Tab}s
     * in the given {@code TabPane}.
     * <p>Note: This method completely replaces whatever used to be in the
     * {@code TabPane}'s {@code setOnKeyPressed()} method</p>
     *
     * @param tabPane The {@code TabPane} to add the keybinds to
     */
    public static void addCloseTabKeys(final TabPane tabPane) {
        //TODO: Make this 'append' the keybinds to the already present setOnKeyPressed() method, so they are not removed
        tabPane.setOnKeyPressed(new EventHandler <KeyEvent>() {
            @Override
            public void handle(final KeyEvent event) {
                if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN).match(event)) {
                    tabPane.getTabs().clear();
                }
                else if (new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN).match(event)) {
                    final int tabIndex = tabPane.getSelectionModel().getSelectedIndex(); //just Ctrl + W
                    if (-1 != tabIndex) {
                        tabPane.getTabs().remove(tabIndex); //ArithmeticException (division by zero) thrown if tabs closed too fast
                    }
                }
            }
        });
    }

    /**
     * Creates and returns an {@code Alert} window to be displayed.
     *
     * @param type       The {@code Alert.AlertType} of the alert.
     * @param title      The title text of the {@code Alert}
     * @param headerText The header text of the {@code Alert}
     * @param message    The message of the {@code Alert}
     *
     * @return The created {@code Alert}
     */
    public static Alert createAlert(final Alert.AlertType type, final String title, final String headerText, final String message) {
        final Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);

        return alert;
    }

    /**
     * Creates and returns an {@code Alert} with a text box to be displayed
     *
     * @param type            The {@code Alert.AlertType} of the alert. If this is {@code Alert.AlertType.ERROR}, then
     *                        {@code message} is printed to {@code System.err}
     * @param title           The title text of the {@code Alert}
     * @param headerText      The header text of the {@code Alert}
     * @param message         The message of the {@code Alert}
     * @param textAreaContent The content of the {@code TextArea} in the {@code Alert}
     * @param editable        Whether or not the user should be able to edit the text in the {@code TextArea}
     *
     * @return The created {@code Alert}
     */
    public static Alert createAlertWithTextBox(final Alert.AlertType type, final String title, final String headerText,
                                               final String message, final String textAreaContent, final boolean editable) {

        final Alert alert = createAlert(type, title, headerText, message);

        final TextArea textArea = new TextArea(textAreaContent);
        textArea.setEditable(editable);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        final GridPane expandableContent = new GridPane();
        expandableContent.setMaxWidth(Double.MAX_VALUE);
        expandableContent.add(textArea, 0, 0);

        alert.getDialogPane().setExpandableContent(expandableContent);
        alert.getDialogPane().setExpanded(true);

        return alert;
    }

    /**
     * Creates and returns an {@code Dialog <Pair <String, String>>} with two {@code TextField}s to be displayed
     *
     * @param title             The title text of the {@code Dialog}
     * @param headerText        The header text of the {@code Dialog}
     * @param firstLabelString  The label for the first {@code TextField}
     * @param secondLabelString The label for the second {@code TextField}
     * @param okButtonText      The text for the OK button
     *
     * @return The created {@code Dialog <Pair <String, String>>}
     */
    public static Dialog <Pair <String, String>> createDualTextFieldDialog(final String title, final String headerText,
                                                                           final String firstLabelString,
                                                                           final String secondLabelString,
                                                                           final String okButtonText) {

        final Dialog <Pair <String, String>> dialog = new Dialog <Pair <String, String>>();
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);

        final ButtonType okButton = new ButtonType(okButtonText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        final TextField firstField = new TextField();
        firstField.setPromptText(firstLabelString);

        final TextField secondField = new TextField();
        secondField.setPromptText(secondLabelString);

        final GridPane dialogPane = new GridPane();

        dialogPane.add(firstField, 1, 0);
        dialogPane.add(secondField, 1, 1);

        dialog.getDialogPane().setContent(dialogPane);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                firstField.requestFocus();
            }
        });

        dialog.setResultConverter(new Callback <ButtonType, Pair <String, String>>() {
            @Override
            public Pair <String, String> call(final ButtonType param) {
                if (param == okButton) {
                    return new Pair <String, String>(firstField.getText(), secondField.getText());
                }
                return null;
            }
        });

        return dialog;
    }
}
