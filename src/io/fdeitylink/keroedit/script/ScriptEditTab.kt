package io.fdeitylink.keroedit.script

import java.text.MessageFormat

import java.nio.file.Path
import java.nio.file.Files

import java.io.IOException
import java.io.FileNotFoundException

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

class ScriptEditTab @Throws(IOException::class) constructor(inPath: Path): FileEditTab(inPath) {
    private val textArea: TextArea

    private var parent: MapEditTab?

    init {
        parent = null

        //TODO: Verify p represents a file
        val p = inPath.toAbsolutePath()

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

        var scriptText = ""
        try {
            Files.lines(p, Charset.forName("Shift_JIS")).use {
                //TODO: Replace all newlines with \n? (use regex)
                scriptText = it.collect(Collectors.joining("\n"))
            }
        }
        catch (except: FileNotFoundException) {
            try {
                Files.createFile(p) //TODO: Give attributes as well?
            }
            catch (except: IOException) {
                //TODO: Do nothing or let the except escalate?
            }
        }
        catch (except: IOException) {
            FXUtil.createAlert(type = Alert.AlertType.ERROR, title = Messages.getString("ScriptEditTab.IOExcept.TITLE"),
                               message = MessageFormat.format(Messages.getString("Keroedit.IOExcept.MESSAGE"),
                                                              p.fileName, except.message)).showAndWait()
            throw except
        }

        textArea = TextArea(scriptText)
        textArea.requestFocus()
        textArea.font = Font.font("Consolas", 12.0)
        textArea.textProperty().addListener { _, _, _ -> markChanged() }

        /*
         * Assume that this ScriptEditTab is not inside of a
         * MapEditTab and set the title to the filename. The
         * other constructor will re-set the text if this
         * tab is inside of a MapEditTab.
         */
        text = p.baseFilename(GameData.scriptExtension)

        content = textArea
    }

    //TODO: Make this constructor the primary one?
    constructor(inPath: Path, parent: MapEditTab): this(inPath) {
        this.parent = parent
        text = Messages.getString("ScriptEditTab.TITLE")
    }

    override fun undo() = textArea.undo()

    override fun redo() = textArea.redo()

    override fun save() {
        try {
            Files.write(path, textArea.paragraphs, Charset.forName("Shift_JIS"))
            markUnchanged()
        }
        catch (except: IOException) {
            FXUtil.createAlert(type = Alert.AlertType.ERROR, title = Messages.getString("ScriptEditTab.Save.IOExcept.TITLE"),
                               message = MessageFormat.format(Messages.getString("ScriptEditTab.Save.IOExcept.MESSAGE"),
                                                              path.fileName, except.message)).showAndWait()
        }
    }

    override fun markChanged() {
        super.markChanged()
        parent?.markChanged()
    }

    override public fun markUnchanged() = super.markUnchanged()
}