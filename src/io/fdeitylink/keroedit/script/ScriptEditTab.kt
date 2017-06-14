package io.fdeitylink.keroedit.script

import java.text.MessageFormat

import java.nio.file.Path
import java.nio.file.Files

import java.io.IOException

import java.nio.charset.Charset

import java.util.stream.Collectors

import javafx.scene.control.Alert

import javafx.scene.control.TextArea
import javafx.scene.text.Font

import io.fdeitylink.util.baseFilename

import io.fdeitylink.util.use

import io.fdeitylink.util.fx.FXUtil
import io.fdeitylink.util.fx.FileEditTab

import io.fdeitylink.keroedit.Messages

import io.fdeitylink.keroedit.gamedata.GameData

import io.fdeitylink.keroedit.mapedit.MapEditTab
import io.fdeitylink.util.Logger

class ScriptEditTab
@Throws(IOException::class) constructor(inPath: Path,
                                        private val parent: MapEditTab? = null
) : FileEditTab(inPath.toAbsolutePath().apply {
    if (!Files.exists(this)) {
        try {
            Files.createFile(this)
        }
        catch (except: IOException) {
            Logger.logThrowable("Error creating new script file $this", except)
        }
    }
}) {

    private val textArea = TextArea()

    init {
        /*
         * TODO: Fix this weird exception that I can't seem to get myself
         * java.io.UncheckedIOException: java.nio.charset.MalformedInputException: Input length = 1
	     * java.io.BufferedReader$1.hasNext(Unknown Source)
	     * java.util.Iterator.forEachRemaining(Unknown Source)
         * java.util.Spliterators$IteratorSpliterator.forEachRemaining(Unknown Source)
         * java.util.stream.AbstractPipeline.copyInto(Unknown Source)
         * java.util.stream.AbstractPipeline.wrapAndCopyInto(Unknown Source)
         * java.util.stream.ReduceOps$ReduceOp.evaluateSequential(Unknown Source)
         * java.util.stream.AbstractPipeline.evaluate(Unknown Source)
         * java.util.stream.ReferencePipeline.collect(Unknown Source)
         * io.fdeitylink.keroedit.script.ScriptEditTab.<init>(ScriptEditTab.java:44)
         *  - scriptText = lineStream.collect(Collectors.joining("\n"));
         */

        val scriptText = try {
            Files.lines(path, Charset.forName("Shift_JIS")).use {
                //TODO: Replace all newlines with \n? (use regex)
                it.collect(Collectors.joining("\n"))
            }
        }
        catch (except: IOException) {
            FXUtil.createAlert(type = Alert.AlertType.ERROR,
                               title = Messages["ScriptEditTab.IOExcept.TITLE"],
                               message = MessageFormat.format(Messages["Keroedit.IOExcept.MESSAGE"],
                                                              path.fileName, except.message)).showAndWait()
            //TODO: Is this necessary?
            tabPaneProperty().addListener { _, _, _ -> tabPane?.tabs?.remove(this) }
            throw except
        }

        textArea.text = scriptText
        textArea.requestFocus()
        textArea.font = Font.font("Consolas", 12.0)
        textArea.textProperty().addListener { _, _, _ -> markChanged() }

        text = if (null == parent) path.baseFilename(GameData.scriptExtension) else Messages["ScriptEditTab.TITLE"]

        content = textArea
    }

    override fun undo() = textArea.undo()

    override fun redo() = textArea.redo()

    override fun save() {
        try {
            Files.write(path, textArea.paragraphs, Charset.forName("Shift_JIS"))
            markUnchanged()
        }
        catch (except: IOException) {
            FXUtil.createAlert(type = Alert.AlertType.ERROR, title = Messages["ScriptEditTab.Save.IOExcept.TITLE"],
                               message = MessageFormat.format(Messages["ScriptEditTab.Save.IOExcept.MESSAGE"],
                                                              path.fileName, except.message)).showAndWait()
        }
    }

    override fun markChanged() {
        super.markChanged()
        parent?.markChanged()
    }

    override public fun markUnchanged() = super.markUnchanged()
}