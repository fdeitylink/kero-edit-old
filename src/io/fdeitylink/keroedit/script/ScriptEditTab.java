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

import javafx.scene.control.Tooltip;

import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

import io.fdeitylink.keroedit.util.NullArgumentException;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.util.FXUtil;

import io.fdeitylink.keroedit.mapedit.MapEditTab;

//TODO: Make this a static inner class of MapEditTab?
public final class ScriptEditTab extends FXUtil.FileEditTab {
    private final Path script;

    private MapEditTab parent;

    private final TextArea textArea;

    public ScriptEditTab(final Path inScript) throws IOException {
        script = inScript;

        String scriptText = "";
        try (Stream <String> lineStream = Files.lines(inScript, Charset.forName("SJIS"))) {
            scriptText = lineStream.collect(Collectors.joining("\n"));
        }
        catch (final FileNotFoundException except) {
            //TODO: Create new script file
            System.err.println("ERROR: Could not locate PXEVE file " + inScript.toAbsolutePath());
        }
        catch (final IOException except) {
            FXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("ScriptEditTab.IOExcept.TITLE"), null,
                               MessageFormat.format(Messages.getString("ScriptEditTab.IOExcept.MESSAGE"),
                                                    inScript.getFileName(), except.getMessage())).showAndWait();
            throw except;
        }

        textArea = new TextArea(scriptText);
        textArea.requestFocus();
        textArea.setFont(new Font("Consolas", 12));

        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            setChanged(true);
            if (null != parent) {
                parent.setChanged(true);
            }
        });

        setId(inScript.toAbsolutePath().toString());

        //assume this tab is not inside MapEditTab - other constructor will call setText() if it is inside MapEditTab
        setText(inScript.getFileName().toString());
        setTooltip(new Tooltip(inScript.toAbsolutePath().toString()));

        setContent(textArea);
    }

    public ScriptEditTab(final Path inScript, final MapEditTab parent) throws IOException {
        this(inScript);

        if (null == parent) {
            throw new NullArgumentException("ScriptEditTab", "parent");
        }

        this.parent = parent;
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
        try {
            Files.write(script, textArea.getParagraphs(), Charset.forName("SJIS"));
            setChanged(false);
        }
        catch (final IOException except) {
            FXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("ScriptEditTab.Save.IOExcept.TITLE"), null,
                               MessageFormat.format(Messages.getString("ScriptEditTab.Save.IOExcept.MESSAGE"),
                                                    script.getFileName(), except.getMessage())).showAndWait();
        }
    }

    @Override
    public void setChanged(final boolean changed) {
        super.setChanged(changed);
    }
}