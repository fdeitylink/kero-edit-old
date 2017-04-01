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

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.util.JavaFXUtil;

import io.fdeitylink.keroedit.util.FileEditTab;

public final class ScriptEditTab extends FileEditTab {
    private final Path script;

    private final TextArea textArea;

    public ScriptEditTab(final Path inScript, final boolean global) {
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
            JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("ScriptEditTab.IOExcept.TITLE"), null,
                                   MessageFormat.format(Messages.getString("ScriptEditTab.IOExcept.MESSAGE"),
                                                        inScript.getFileName(),
                                                        except.getMessage())).showAndWait();
            getTabPane().getTabs().remove(this);
        }

        textArea = new TextArea(scriptText);
        textArea.requestFocus();
        textArea.setFont(new Font("Consolas", 12));
        textArea.textProperty().addListener(((observable, oldValue, newValue) -> setChanged(true)));

        setId(inScript.toAbsolutePath().toString());

        setText(global ? inScript.getFileName().toString() : Messages.getString("ScriptEditTab.TITLE"));
        setTooltip(new Tooltip(inScript.toAbsolutePath().toString()));

        setContent(textArea);
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
        setChanged(false);
        //TODO: actually save
    }
}