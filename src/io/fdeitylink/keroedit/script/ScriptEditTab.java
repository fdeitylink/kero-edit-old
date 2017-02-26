package io.fdeitylink.keroedit.script;

import java.text.MessageFormat;

import java.io.File;

import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

import io.fdeitylink.keroedit.Messages;
import io.fdeitylink.keroedit.util.FileEditTab;
import io.fdeitylink.keroedit.util.JavaFXUtil;
import javafx.scene.control.Alert;

import javafx.scene.control.Tooltip;

import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

import io.fdeitylink.keroedit.util.Logger;

public class ScriptEditTab extends FileEditTab {
    private final File scriptFile;

    private final TextArea textArea;

    public ScriptEditTab(final File inFile, final boolean global) {
        scriptFile = inFile;

        String scriptText = "";

        FileInputStream inStream = null;
        FileChannel chan = null;

        //TODO: Use Reader?
        try {
            inStream = new FileInputStream(inFile);
            chan = inStream.getChannel();
            final ByteBuffer buf = ByteBuffer.allocate((int)inFile.length());
            chan.read(buf);
            scriptText = new String(buf.array(), "SJIS");
        }
        catch (final UnsupportedEncodingException except) {
            //TODO: throw error/show message?
            System.err.println(Messages.getString("UNSUPPORTED_SJIS_ENCODING"));
        }
        catch (final FileNotFoundException except) {
            //TODO: Create new script file
            System.err.println("ERROR: Could not locate PXEVE file " + inFile.getName());
        }
        catch (final IOException except) {
            JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("ScriptEditTab.IOExcept.TITLE"), null,
                                   MessageFormat.format(Messages.getString("ScriptEditTab.IOExcept.MESSAGE"), inFile.getName(),
                                                        except.getMessage())).showAndWait();
            getTabPane().getTabs().remove(this);
        }
        finally {
            try {
                if (null != inStream) {
                    inStream.close();
                }
                if (null != chan) {
                    chan.close();
                }
            }
            catch (final IOException except) {
                Logger.logException(MessageFormat.format(Messages.getString("PxPack.CLOSE_FAIL"), inFile.getName()),
                                    except);
                //TODO: Probably something should be done if the script file can't be closed
            }
        }

        textArea = new TextArea(scriptText);
        textArea.requestFocus();
        textArea.setFont(new Font("Consolas", 12));
        textArea.textProperty().addListener(((observable, oldValue, newValue) -> setChanged(true)));

        setText(global ? inFile.getName() : Messages.getString("ScriptEditTab.TITLE"));
        setTooltip(new Tooltip(inFile.getAbsolutePath()));
        setId(inFile.getAbsolutePath());

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
        //TODO: save
    }
}