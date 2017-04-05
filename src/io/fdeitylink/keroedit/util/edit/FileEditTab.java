package io.fdeitylink.keroedit.util.edit;

import java.util.ArrayDeque;

import javafx.scene.Node;
import javafx.scene.control.Tab;

public abstract class FileEditTab extends Tab {
    private final ArrayDeque <UndoableEdit> undoStack = new ArrayDeque <>();
    private final ArrayDeque <UndoableEdit> redoStack = new ArrayDeque <>();

    private boolean changed;

    public FileEditTab() {
        changed = false;
    }

    public FileEditTab(final String text) {
        super(text);
    }

    public FileEditTab(final String text, final Node content) {
        super(text, content);
        changed = false;
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

    protected boolean getChanged() {
        return changed;
    }

    protected final void setChanged(final boolean changed) {
        this.changed = changed;
        if (getText().endsWith("*")) {
            if (!changed) {
                final String text = getText();
                setText(text.substring(0, text.lastIndexOf("*")));
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