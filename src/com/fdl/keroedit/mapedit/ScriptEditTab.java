/*
 * TODO:
 * Create PXEVE class and use that
 * Syntax highlighting
 * Figure out what to do for when two maps share one script (should script editor go on main TabPane?
 *  - Have this TextArea reference the other and copy when the other closes?
 * When script is renamed in the Properties tab, have it rename this
 * Set title to Script - [name]
 * Keep newlines in mind for saving
 * Make this a window rather than a tab?
 */

package com.fdl.keroedit.mapedit;

import java.io.File;

import java.io.FileNotFoundException;
import java.util.Scanner;

import javafx.scene.control.Tab;

import javafx.scene.control.TextArea;

import javafx.application.Platform;

import com.fdl.keroedit.gamedata.GameData;

public class ScriptEditTab extends Tab {
    private final File script;
    private /*final*/ TextArea textEditorArea;

    public ScriptEditTab(final String inFileBaseName) {
        final File inFile = new File(GameData.getResourceFolder().getAbsolutePath() +
                                     File.separatorChar + "text" + File.separatorChar +
                                     inFileBaseName + ".pxeve");
        script = inFile;

        String scriptContents = "";
        try {
            Scanner scan = new Scanner(inFile);
            while (scan.hasNext()) {
                scriptContents += scan.next();
            }
        }
        catch (final FileNotFoundException except) {
            //TODO: Create new script file?
            System.err.println("ERROR: Could not locate PXEVE file " + inFile.getName());
            return;
        }

        textEditorArea = new TextArea(scriptContents);

        setText(inFile.getName().replace(".pxeve", ""));
        setId(inFile.getName().replace(".pxeve", ""));

        setContent(textEditorArea);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (null != getTabPane()) {
                    textEditorArea.setPrefHeight(getTabPane().getPrefHeight());
                    textEditorArea.setPrefWidth(getTabPane().getPrefWidth());
                }
            }
        });
    }
}