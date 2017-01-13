package com.fdl.keroedit;

public interface Changeable {
    boolean isChanged();
    boolean markChanged();
    void undo();
    void redo();
}
