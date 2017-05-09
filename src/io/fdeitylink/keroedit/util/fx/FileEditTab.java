package io.fdeitylink.keroedit.util.fx;

import java.util.ArrayDeque;

import javafx.scene.Node;

import javafx.scene.control.Alert;

import javafx.scene.control.Tab;

import javafx.scene.control.ButtonType;

import io.fdeitylink.keroedit.Messages;

//TODO: Add abstract getPath(), rename() methods?
//TODO: Extend PoppableTab
public abstract class FileEditTab extends Tab {
    //TODO: Store undo pointer to mark unchanged on undo/redo if same state as when saved is met
    private final ArrayDeque <UndoableEdit> undoStack = new ArrayDeque <>();
    private final ArrayDeque <UndoableEdit> redoStack = new ArrayDeque <>();

    private boolean changed;

    protected FileEditTab() {
        this(null, null);
    }

    protected FileEditTab(final String text) {
        this(text, null);
    }

    protected FileEditTab(final String text, final Node content) {
        super(text, content);
        changed = false;

        setOnCloseRequest(event -> {
            if (isChanged()) {
                final String title = /*getLabelText();*/getText();
                final Alert alert = FXUtil.createAlert(Alert.AlertType.NONE,
                                                       title.substring(0, title.lastIndexOf('*')), null,
                                                       Messages.getString("FXUtil.FileEditTab.UNSAVED_CHANGES"));

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
            markChanged();
            final UndoableEdit edit = undoStack.removeFirst();
            redoStack.addFirst(edit);
            edit.undo();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            markChanged();
            final UndoableEdit edit = redoStack.removeFirst();
            undoStack.addFirst(edit);
            edit.redo();
        }
    }

    //TODO: default implementation here that calls markUnchanged() but is required to be overloaded?
    public abstract void save();

    public boolean isChanged() {
        return changed;
    }

    //TODO: Handle subclass tabs that have "*" in their name normally (not as a marker of changes)

    protected void markChanged() {
        if (!changed) {
            changed = true;
            final String text = getText();
            if (!text.endsWith("*")) {
                setText(text + '*');
            }
        }
    }

    protected void markUnchanged() {
        if (changed) {
            changed = false;
            final String text = getText();
            if (text.endsWith("*")) {
                setText(text.substring(0, text.lastIndexOf('*')));
            }
        }
    }

    protected final void addUndo(final UndoableEdit edit) {
        markChanged();
        redoStack.clear();
        undoStack.addFirst(edit);
    }
}