package com.fdl.keroedit.util;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

import javafx.scene.layout.Region;
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

import javafx.scene.control.TextArea;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

public class JavaFXUtil {
    private JavaFXUtil() {

    }

    /**
     * Sets the background color of a given {@code Region}
     *
     * @param color The color of the background
     * @param region The region to apply the background color to
     */
    public static void setBackgroundColor(final Color color, final Region region) {
        region.setBackground(new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /**
     * Returns a {@code String} representation of the Color suitable for {@code Color.web()} or {@code Color.valueOf()}.
     * Note that it disregards opacity
     *
     * @param color The {@code Color} to convert to a web string
     *
     * @return A web string representing the given {@code Color}
     */
    public static String colorToString(final Color color) {
        return String.format("0x%02X%02X%02X",
                             (int)(color.getRed() * 255),
                             (int)(color.getGreen() * 255),
                             (int)(color.getBlue() * 255));
        //TODO: opacity? (int)(color.getOpacity() * 255)
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
        if (null == src) {
            return null;
        }

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
