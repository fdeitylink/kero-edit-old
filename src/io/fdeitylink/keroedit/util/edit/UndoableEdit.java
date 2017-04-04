package io.fdeitylink.keroedit.util.edit;

public interface UndoableEdit {
    void undo();

    void redo();
}