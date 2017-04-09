package io.fdeitylink.keroedit.util;

import java.util.ArrayDeque;

import javafx.scene.layout.Region;
import javafx.geometry.Insets;

import javafx.scene.Node;

import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.CornerRadii;

import javafx.scene.control.TabPane;
import com.sun.javafx.scene.control.skin.TabPaneSkin;
import com.sun.javafx.scene.control.behavior.TabPaneBehavior;
import javafx.scene.control.Tab;

import javafx.scene.paint.Color;

import javafx.scene.control.Dialog;
import javafx.scene.control.Alert;

import javafx.scene.control.ButtonType;

import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;

import javafx.application.Platform;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

public final class FXUtil {
    private FXUtil() {

    }

    /**
     * Closes a tab such that if it has an {@code onCloseRequest} or
     * {@code onClosed EventHandler} set, it will be triggered.
     *
     * @param tab The tab to close
     *
     * @throws NullArgumentException if {@code tab} is null
     * @throws IllegalStateException if the tab is not inside a {@code TabPane}
     */
    public static void closeTab(final Tab tab) {
        if (null == tab) {
            throw new NullArgumentException("closeTab", "tab");
        }

        final TabPane tabPane = tab.getTabPane();
        if (null == tabPane) {
            throw new IllegalStateException("Attempt to close a tab that is not inside a TabPane");
        }

        //https://stackoverflow.com/a/22783949/7355843
        //assumes default TabPane skin
        final TabPaneBehavior behavior = ((TabPaneSkin)tabPane.getSkin()).getBehavior();
        if (behavior.canCloseTab(tab)) {
            behavior.closeTab(tab);
        }
    }

    /**
     * Sets the maximum length of the text in a {@code TextInputControl}
     *
     * @param input The {@code TextInputConrol} to apply a length limit to
     * @param len The maximum length of the string inside the given {@code TextInputControl}
     */
    public static void setTextFieldLength(final TextInputControl input, final int len) {
        input.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > len) {
                input.textProperty().set(newValue.substring(0, len));
            }
        });
    }

    /**
     * Sets the background image of a given {@code Region}
     *
     * @param image The image of the background
     * @param region The {@code Region} to apply the background image to
     */
    public static void setBackgroundImage(final Image image, final Region region) {
        region.setBackground(new Background(new BackgroundImage(image, null, null, null, null)));
    }

    /**
     * Sets the background color of a given {@code Region}
     *
     * @param color The color of the background
     * @param region The {@code Region} to apply the background color to
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
     * @param src The image to scale
     * @param scale The scale factor to scale the image by
     *
     * @return The result of scaling the given image by the given scale factor.
     * If src is null or the scale is 1, src is returned.
     */
    public static Image scaleImage(final Image src, final int scale) {
        if (null == src || 1 == scale) {
            return src;
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
     * @param type The {@code Alert.AlertType} of the alert.
     * @param title The title text of the {@code Alert}
     * @param headerText The header text of the {@code Alert}
     * @param message The message of the {@code Alert}
     *
     * @return The created {@code Alert}
     */
    public static Alert createAlert(final Alert.AlertType type, final String title, final String headerText,
                                    final String message) {
        final Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);

        return alert;
    }

    /**
     * Creates and returns an {@code Alert} with a text box to be displayed
     *
     * @param type The {@code Alert.AlertType} of the alert
     * @param title The title text of the {@code Alert}
     * @param headerText The header text of the {@code Alert}
     * @param message The message of the {@code Alert}
     * @param textAreaContent The content of the {@code TextArea} in the {@code Alert}
     * @param editable Whether or not the user should be able to edit the text in the {@code TextArea}
     *
     * @return The created {@code Alert}
     */
    public static Alert createTextboxAlert(final Alert.AlertType type, final String title, final String headerText,
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
     * Creates and returns an {@code Dialog <Tuple <String, String>>} with two {@code TextField}s to be displayed
     *
     * @param title The title text of the {@code Dialog}
     * @param headerText The header text of the {@code Dialog}
     * @param firstLabel The label for the first {@code TextField}
     * @param secondLabel The label for the second {@code TextField}
     *
     * @return The created {@code Dialog <Pair <String, String>>}
     */
    public static Dialog <Tuple <String, String>> createDualTextFieldDialog(final String title, final String headerText,
                                                                            final String firstLabel, final String secondLabel) {
        final Dialog <Tuple <String, String>> dialog = new Dialog <>();
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);

        //final ButtonType okButton = new ButtonType(okButtonText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final TextField firstField = new TextField();
        firstField.setPromptText(firstLabel);

        final TextField secondField = new TextField();
        secondField.setPromptText(secondLabel);

        final GridPane dialogPane = new GridPane();

        dialogPane.add(firstField, 1, 0);
        dialogPane.add(secondField, 1, 1);

        dialog.getDialogPane().setContent(dialogPane);

        Platform.runLater(firstField::requestFocus);

        dialog.setResultConverter(param -> ButtonType.OK == param ? new Tuple <>(firstField.getText(), secondField.getText()) :
                                           null);

        return dialog;
    }

    public static abstract class FileEditTab extends Tab {
        private final ArrayDeque <UndoableEdit> undoStack = new ArrayDeque <>();
        private final ArrayDeque <UndoableEdit> redoStack = new ArrayDeque <>();

        private boolean changed;

        public FileEditTab() {
            this(null, null);
        }

        public FileEditTab(final String text) {
            this(text, null);
        }

        public FileEditTab(final String text, final Node content) {
            super(text, content);
            changed = false;

            setOnCloseRequest(event -> {
                if (isChanged()) {
                    final String str = getText();
                    final Alert alert = createAlert(Alert.AlertType.NONE,
                                                    str.substring(0, str.lastIndexOf('*')) + " - Unsaved changes", null,
                                                    "This tab has unsaved changes. Save first?");

                    alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);

                    alert.showAndWait().ifPresent(result -> {
                        if (ButtonType.YES == result) {
                            save();
                        }
                        else if (ButtonType.CANCEL == result) {
                            event.consume();
                        }
                    });
                }
            });
        }

        public void undo() {
            if (!undoStack.isEmpty()) {
                final UndoableEdit edit = undoStack.removeFirst();
                redoStack.addFirst(edit);
                edit.undo();
            }
        }

        public void redo() {
            if (!redoStack.isEmpty()) {
                final UndoableEdit edit = redoStack.removeFirst();
                undoStack.addFirst(edit);
                edit.redo();
            }
        }

        //TODO: default implementation here marks unchanged but is required to be overloaded?
        public abstract void save();

        protected boolean isChanged() {
            return changed;
        }

        protected void setChanged(final boolean changed) {
            this.changed = changed;
            if (getText().endsWith("*")) {
                if (!changed) {
                    final String text = getText();
                    setText(text.substring(0, text.lastIndexOf('*')));
                }
            }
            else if (changed) {
                setText(getText() + "*");
            }
        }

        protected ArrayDeque <UndoableEdit> getUndoStack() {
            return undoStack;
        }

        protected ArrayDeque <UndoableEdit> getRedoStack() {
            return redoStack;
        }
    }
}