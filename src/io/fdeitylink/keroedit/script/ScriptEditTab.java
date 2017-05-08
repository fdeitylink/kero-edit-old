package io.fdeitylink.keroedit.script;

import java.text.MessageFormat;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import java.nio.charset.Charset;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;
import java.io.FileNotFoundException;

import io.fdeitylink.keroedit.util.fx.FileEditTab;
import javafx.scene.control.Alert;

import javafx.scene.control.Tooltip;

import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

import io.fdeitylink.keroedit.util.NullArgumentException;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.util.fx.FXUtil;

import io.fdeitylink.keroedit.mapedit.MapEditTab;

public final class ScriptEditTab extends FileEditTab {
    private final Path scriptPath;

    private final TextArea textArea;

    public ScriptEditTab(final Path inPath) throws IOException {
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

        //TODO: Check if this is actually within the mod folder and throw except if not?
        scriptPath = NullArgumentException.requireNonNull(inPath, "ScriptEditTab", "inPath").toAbsolutePath();

        String scriptText = "";
        try (Stream <String> lineStream = Files.lines(inPath, Charset.forName("Shift_JIS"))) {
            scriptText = lineStream.collect(Collectors.joining("\n"));
        }
        catch (final FileNotFoundException except) {
            try {
                Files.createFile(inPath);
            }
            catch (final IOException ex) {
                //do nothing or throw except?
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

        setId(scriptPath.toString());

        //assume this tab is not inside MapEditTab - other constructor will call setText() if it is inside MapEditTab
        setText(inPath.getFileName().toString());
        setTooltip(new Tooltip(scriptPath.toString()));

        setContent(textArea);
    }

    public ScriptEditTab(final Path inPath, final MapEditTab parent) throws IOException {
        this(inPath); //throws NullArgumentException if inPath == null

        NullArgumentException.requireNonNull(parent, "ScriptEditTab", "parent");
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            markChanged();
            parent.markChanged();
        });

        setText(Messages.getString("ScriptEditTab.TITLE"));
    }

    public Path getScriptPath() {
        return scriptPath;
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