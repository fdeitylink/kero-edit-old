/*
 * TODO:
 * Somehow override setText() and setGraphic() so setText() sets label, setGraphic() throws except
 * CONTENT_PANE doesn't fill space and thereby confines its elements
 * Allow setting text to null (doesn't work bc it is always null by the time another class is using textProperty()
 */

package io.fdeitylink.keroedit.util.fx;

import javafx.stage.Stage;
import javafx.scene.Scene;

import javafx.stage.WindowEvent;

import javafx.scene.layout.Pane;

import javafx.scene.Node;

import javafx.scene.control.Tab;

import javafx.scene.control.Label;

import javafx.scene.input.MouseButton;


public class PoppableTab extends Tab {
    private final Label textLabel;

    /**
     * A {@code Stage} that allows this {@code PoppableTab} to be
     * "popped" out of its {@code TabPane}. When this {@code Stage}
     * is displayed, the tab's content moves to it. When it is closed
     * or hidden, the content moves back to the tab.
     */
    private final Stage contentStage;

    /**
     * A completely empty {@code Pane} used in lieu of the @code null}
     * value because the root of an {@code Scene} (which a {@code Stage}
     * uses) cannot be null.
     */
    //TODO: Find or create immutable Pane class
    private final Pane EMPTY_PANE;

    //TODO: Create Pane class that allows only one child and has get/setChild() methods
    /**
     * A {@code Pane} that holds the core "content" of the tab or
     * its {@code contentStage} when that is displayed. If the
     * content of this {@code PoppableTab} were to be an
     * {@code ImageView}, then this Pane would hold that.
     * This {@code Pane} is swapped between this
     * {@code PoppableTab} and its {@code contentStage}, depending
     * on if the {@code contentStage} is being shown or not.
     */
    private final Pane CONTENT_PANE;

    /**
     * A {@code boolean} flag for when the content of this
     * {@code PoppableTab} is set to null in order to allow it to
     * swap what it was holding with what the {@code contentStage}
     * was holding. When the content of this {@code PoppableTab} is
     * changed, even to {@code null}, a {@code ChangeListener} is
     * fired that is not supposed to run any code if the only reason
     * the {@code PoppableTab's} content was changed is to clear it.
     */
    private boolean contentNullToClear = false;

    /**
     * A {@code boolean} flag for when the text of this
     * {@code PoppableTab} is set to null in order to clear it and
     * set the text of this {@code PoppableTab's Label}
     * (see initPoppingSystem()) to the value the text used to
     * hold. When the text of this {@code PoppableTab} is changed,
     * even to {@code null}, a {@code ChangeListener} is fired
     * that does not run any code if the only reason the
     * {@code PoppableTab's} text was changed is to clear it.
     */
    private boolean textNullToClear = false;

    public PoppableTab() {
        this(null, null);
    }

    public PoppableTab(final String text) {
        this(text, null);
    }

    public PoppableTab(final String text, final Node content) {
        super(null, content);

        textLabel = new Label(text);

        EMPTY_PANE = new Pane();
        CONTENT_PANE = new Pane();

        contentStage = new Stage();

        initPoppingSystem();
    }

    protected final String getLabelText() {
        return textLabel.getText();
    }

    private void initPoppingSystem() {
        setGraphic(textLabel);

        textProperty().addListener((observable, oldValue, newValue) -> {
            /*
             * In an ideal world I would be able to override setText() without
             * extra reflection libraries in order to do the following in a less
             * complicated manner and before the text even changes, but I can't.
             */
            if (!textNullToClear) {
                if (null == newValue) {
                    System.out.println("setText(null) called externally");
                    textLabel.setText(null);
                }
                else {
                    textLabel.setText(newValue);
                    textNullToClear = true;
                    setText(null);
                    textNullToClear = false;
                }
            }
        });

        setContent(CONTENT_PANE);

        contentStage.titleProperty().bind(textLabel.textProperty());
        contentStage.setScene(new Scene(EMPTY_PANE));

        contentStage.setOnCloseRequest(event -> {
            contentNullToClear = true;
            setContent(null); //removes EMPTY_PANE from tab' content, thereby allowing it to be set as root below
            contentNullToClear = false;

            contentStage.getScene().setRoot(EMPTY_PANE);
            contentStage.close();
            event.consume();

            setContent(CONTENT_PANE);
        });

        contentStage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> {
            contentNullToClear = true;
            setContent(null);
            contentNullToClear = false;

            contentStage.getScene().setRoot(CONTENT_PANE);
            contentStage.setWidth(CONTENT_PANE.getWidth());
            contentStage.setHeight(CONTENT_PANE.getHeight());

            setContent(EMPTY_PANE);
        });

        contentProperty().addListener((observable, oldValue, newValue) -> {
            /*
             * This listener is not for when contentStage is shown or hidden.
             * It is for when, for example, the content of the tab is changed
             * from one ImageView to another, or from one Label to another,
             * or even from an ImageView to a Label. So the if block ensures
             * that this event wasn't triggered just because the stage was
             * shown or hidden.
             * In an ideal world I would be able to override setContent() without
             * extra reflection libraries in order to do the following in a less
             * complicated manner and before the content even changes, but I can't.
             */
            if (!contentNullToClear) {
                if (null != newValue) {
                    if (EMPTY_PANE != newValue) { //not a call from another EventHandler set in this method
                        if (contentStage.isShowing()) {
                            setContent(EMPTY_PANE); //reset tab's content to be empty
                            //put newValue into CONTENT_PANE
                            CONTENT_PANE.getChildren().clear();
                            CONTENT_PANE.getChildren().add(newValue);

                            //contentStage.getScene().setRoot(CONTENT_PANE);
                        }
                        else if (CONTENT_PANE != newValue) { //not a call from another EventHandler set in this method
                            CONTENT_PANE.getChildren().clear();
                            CONTENT_PANE.getChildren().add(newValue);
                            setContent(CONTENT_PANE);
                        }
                    }
                }
                else { //content set to null, so clear CONTENT_PANE's children
                    //setContent(CONTENT_PANE); //why not do this? seems right but I guess it works w/o...
                    CONTENT_PANE.getChildren().clear();
                }
            }
        });

        setOnSelectionChanged(event -> contentStage.setIconified(!isSelected()));

        //TODO: Allow Enter key to also show contentStage
        textLabel.setOnMouseClicked(event -> {
            if (MouseButton.PRIMARY == event.getButton() && 2 == event.getClickCount() && !contentStage.isShowing()) {
                contentStage.show();
            }
        });

        setOnClosed(event -> contentStage.close());
    }
}