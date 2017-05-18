package io.fdeitylink.keroedit.script;

import java.text.MessageFormat;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import java.nio.charset.Charset;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;
import java.io.FileNotFoundException;

import javafx.scene.control.Alert;

import javafx.scene.control.TextArea;
import javafx.scene.text.Font;


import io.fdeitylink.util.NullArgumentException;

import io.fdeitylink.util.UtilsKt;

import io.fdeitylink.util.fx.FileEditTab;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.util.fx.FXUtil;

import io.fdeitylink.keroedit.gamedata.GameData;

import io.fdeitylink.keroedit.mapedit.MapEditTab;

public final class ScriptEditTab extends FileEditTab {
    private final TextArea textArea;

    public ScriptEditTab(Path inPath) throws IOException {
        //TODO: Check if inPath is actually within the mod folder and throw except if not?
        super(inPath = inPath.toAbsolutePath());

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

        String scriptText = "";
        try (Stream <String> lines = Files.lines(inPath, Charset.forName("Shift_JIS"))) {
            scriptText = lines.collect(Collectors.joining("\n"));
        }
        catch (final FileNotFoundException except) {
            try {
                Files.createFile(inPath);
            }
            catch (final IOException ex) {
                //TODO: Do nothing or throw except?
            }
        }
        catch (final IOException except) {
            FXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("ScriptEditTab.IOExcept.TITLE"), null,
                               MessageFormat.format(Messages.getString("ScriptEditTab.IOExcept.MESSAGE"),
                                                    inPath.getFileName(), except.getMessage())).showAndWait();
            throw except;
        }

        textArea = new TextArea(scriptText);
        textArea.requestFocus();
        textArea.setFont(new Font("Consolas", 12));

        /*
         * Assume that this ScriptEditTab is not inside of a
         * MapEditTab and set the title to the filename. The
         * other constructor will call setText() again if this
         * tab is inside of a MapEditTab.
         */
        setText(UtilsKt.baseFilename(inPath, GameData.scriptExtension));

        setContent(textArea);
    }

    public ScriptEditTab(final Path inPath, final MapEditTab parent) throws IOException {
        this(inPath); //throws NullArgumentException if inPath == null

        NullArgumentException.Companion.requireNonNull(parent, "ScriptEditTab", "parent");
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            markChanged();
            parent.markChanged();
        });

        setText(Messages.getString("ScriptEditTab.TITLE"));
    }

    @Override
    public void undo() {
        textArea.undo();
    }

    @Override
    public void redo() {
        textArea.redo();
    }

    @Override
    public void save() {
        final Path scriptPath = getPath();
        try {
            Files.write(scriptPath, textArea.getParagraphs(), Charset.forName("Shift_JIS"));
            markUnchanged();
        }
        catch (final IOException except) {
            FXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("ScriptEditTab.Save.IOExcept.TITLE"), null,
                               MessageFormat.format(Messages.getString("ScriptEditTab.Save.IOExcept.MESSAGE"),
                                                    scriptPath.getFileName(), except.getMessage())).showAndWait();
        }
    }

    //Made public so the parent MapEditTab can call it
    @Override
    public void markUnchanged() {
        super.markUnchanged();
    }
}