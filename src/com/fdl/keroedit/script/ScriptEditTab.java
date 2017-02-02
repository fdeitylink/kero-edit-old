package com.fdl.keroedit.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import javafx.scene.control.Tab;

import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.gamedata.GameData;

public class ScriptEditTab extends Tab {
    private final File script;

    public ScriptEditTab(final String scriptName) {
        //TODO: Make friendly for fix.pxeve, island.pxeve, and explain.pxeve
        final File inFile = new File(GameData.getResourceFolder().getAbsolutePath() +
                                     File.separatorChar + "text" + File.separatorChar + scriptName + ".pxeve");
        script = inFile;

        final StringBuilder sBuilder = new StringBuilder();
        try {
            final Scanner scan = new Scanner(inFile);
            while (scan.hasNext()) {
                sBuilder.append(scan.next());
                sBuilder.append("\r\n");
            }
        }
        catch (final FileNotFoundException except) {
            //TODO: Create new script file
            System.err.println("ERROR: Could not locate PXEVE file " + inFile.getName());
        }

        setTooltip(new Tooltip(inFile.getAbsolutePath()));

        final TextArea textArea = new TextArea(sBuilder.toString());
        textArea.requestFocus();
        textArea.setFont(new Font("Consolas", 12));

        setText(Messages.getString("MapEditTab.ScriptEditTab.TITLE"));
        setId(scriptName);

        setContent(textArea);
    }
}