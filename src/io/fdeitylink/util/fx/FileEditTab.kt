/*
 * TODO:
 * abstract fun rename()?
 * Extend PoppableTab
 * Store undo pointer to call markUnchanged() on undo()/redo() if the same state as when save() was called is met
 * Create abstract class representing a savable object and use that rather than Path and delegating save() to subclasses?
 * Handle tabs that have a '*' in their name not as a marker of changes (is this already handled?)
 */

package io.fdeitylink.util.fx

import java.util.ArrayDeque

import java.nio.file.Path

import javafx.scene.Node

import javafx.event.Event
import javafx.event.EventHandler

import javafx.scene.control.ButtonType

import javafx.scene.control.Tab
import javafx.scene.control.Tooltip

import io.fdeitylink.keroedit.Messages

abstract class FileEditTab protected constructor(p: Path, text: String?, content: Node?) : Tab(text, content) {
    private val undoStack = ArrayDeque<UndoableEdit>()
    private val redoStack = ArrayDeque<UndoableEdit>()

    private val filePath: Path = p.toAbsolutePath()

    private var changed = false

    protected constructor(p: Path) : this(p, null, null)

    protected constructor(p: Path, text: String?) : this(p, text, null)

    init {
        id = filePath.toString()
        tooltip = Tooltip(filePath.toString())

        onCloseRequest = EventHandler<Event> { event ->
            if (isChanged()) {
                val alert = FXUtil.createAlert(title = this.text?.substring(0, this.text.lastIndexOf('*')),
                                               message = Messages.getString("FileEditTab.UNSAVED_CHANGES"))

                alert.buttonTypes.addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
                alert.showAndWait().ifPresent {
                    if (ButtonType.YES == it) {
                        save()
                    }
                    else if (ButtonType.CANCEL == it) {
                        event?.consume()
                    }
                }
            }
        }
    }

    fun getPath() = filePath

    open fun undo() {
        if (!undoStack.isEmpty()) {
            markChanged()
            val edit = undoStack.removeFirst()
            redoStack.addFirst(edit)
            edit.undo()
        }
    }

    open fun redo() {
        if (!redoStack.isEmpty()) {
            markChanged()
            val edit = redoStack.removeFirst()
            undoStack.addFirst(edit)
            edit.redo()
        }
    }

    abstract fun save()

    fun isChanged() = changed

    protected open fun markChanged() {
        if (!changed) {
            changed = true
            if (!text.endsWith("*")) {
                text += '*'
            }
        }
    }

    protected open fun markUnchanged() {
        if (changed) {
            changed = false
            if (text.endsWith("*")) {
                text = text.substring(0, text.lastIndexOf("*"))
            }
        }
    }

    protected fun addUndo(edit: UndoableEdit) {
        markChanged()
        redoStack.clear()
        undoStack.addFirst(edit)
    }
}