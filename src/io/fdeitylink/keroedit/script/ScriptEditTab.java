package io.fdeitylink.keroedit.script;

import java.text.MessageFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.nio.channels.SeekableByteChannel;

import java.nio.ByteBuffer;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

import javafx.scene.control.Alert;

import javafx.scene.control.Tooltip;

import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.util.FileEditTab;
import io.fdeitylink.keroedit.util.JavaFXUtil;

public class ScriptEditTab extends FileEditTab {
    private final Path script;

    private final TextArea textArea;

    public ScriptEditTab(final Path inScript, final boolean global) {
        script = inScript;

        String scriptText = "";
        //TODO: Use Reader?
        try (final SeekableByteChannel chan = Files.newByteChannel(inScript, StandardOpenOption.READ)) {
            final ByteBuffer buf = ByteBuffer.allocate((int)Files.size(inScript));
            chan.read(buf);
            scriptText = new String(buf.array(), "SJIS");
        }
        catch (final UnsupportedEncodingException except) {
            //TODO: throw error/show message?
            System.err.println(Messages.getString("UNSUPPORTED_SJIS_ENCODING"));
        }
        catch (final FileNotFoundException except) {
            //TODO: Create new script file
            System.err.println("ERROR: Could not locate PXEVE file " + inScript.toAbsolutePath().toString());
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