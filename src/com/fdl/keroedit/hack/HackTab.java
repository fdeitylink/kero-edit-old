package com.fdl.keroedit.hack;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.fdl.keroedit.util.JavaFXUtil;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.scene.control.Tooltip;

import javafx.scene.control.TreeView;
import javafx.scene.control.TreeItem;

import javafx.geometry.Insets;

import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.Font;

import com.fdl.keroedit.util.FileEditTab;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.resource.ResourceManager;

import com.fdl.keroedit.gamedata.GameData;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;

public class HackTab extends FileEditTab {
    private final SplitPane sPane;
    private final TreeView <String> hacksTree;

    public HackTab() {
        final String stringsFname;
        switch (GameData.getModType()) {
            case KERO_BLASTER:
                stringsFname = "kero_strings.json";
                break;
            case PINK_HOUR:
                stringsFname = "hour_strings.json";
                break;
            case PINK_HEAVEN:
            default:
                stringsFname = "heaven_strings.json";
                break;
        }
        File stringsFile = new File(GameData.getResourceFolder().getAbsolutePath() +
                                    File.separatorChar + "assist" + File.separatorChar + stringsFname);
        if (!stringsFile.exists()) {
            stringsFile = ResourceManager.getFile("assist/" + stringsFname);
        }

        sPane = new SplitPane();

        final HackTreeItem root = new HackTreeItem();
        root.setExpanded(true);

        hacksTree = new TreeView <>(root);
        hacksTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isLeaf()) {
                sPane.getItems().set(1, ((HackTreeItem)newValue).hackPane);
            }
        });

        hacksTree.getRoot().getChildren().add(parseHackFile(stringsFile, Messages.getString("HackTab.Roots.STRINGS")));

        //Get first item of root, which ends up just having children, and then get the first item of that "subroot",
        //which also only has children
        sPane.getItems().addAll(hacksTree, ((HackTreeItem)hacksTree.getRoot().getChildren().get(0)
                                                                   .getChildren().get(0)).hackPane);
        sPane.setDividerPositions(0.2);

        //getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE, ButtonType.APPLY, ButtonType.CANCEL);

        setText(Messages.getString("HackTab.TITLE"));
        setTooltip(new Tooltip(GameData.getExecutable().getAbsolutePath()));

        setContent(sPane);
    }

    @Override
    public void undo() {

    }

    @Override
    public void redo() {

    }

    @Override
    public void save() {
        System.out.println("exe saved");
    }

    @Override
    protected void setChanged(final boolean changed) {
        //no good way to track if something was modified (well not yet - can put change listener on text fields)
        this.changed = changed;
    }

    private HackTreeItem parseHackFile(final File hackFile, final String name) {
        HackTreeItem[] hTreeItems = null;
        try {
            final JsonArray sects = Json.parse(new FileReader(hackFile)).asObject().get("sects").asArray();
            hTreeItems = new HackTreeItem[sects.size()];

            int i = 0;
            for (final JsonValue sect : sects) {
                final GridPane hackPane = new GridPane();
                hackPane.setPadding(new Insets(10, 10, 10, 10));
                hackPane.setVgap(10);
                hackPane.setHgap(10);

                int y = 0;
                for (final JsonValue item : sect.asObject().get("items").asArray()) {
                    final Text label = new Text(item.asObject().getString("label", null));
                    label.setFont(Font.font(null, FontWeight.NORMAL, 12));
                    hackPane.add(label, 0, y);

                    final String currentVal = item.asObject().getString("default", ""); //change to read str from exe
                    final TextField field = new TextField(currentVal);
                    field.setTooltip(new Tooltip("Default: " + item.asObject().getString("default", null)));
                    JavaFXUtil.setTextFieldLength(field, item.asObject().getInt("len", currentVal.length()));
                    hackPane.add(field, 1, y++);

                    //TODO: Store offsets
                }

                hTreeItems[i++] = new HackTreeItem(sect.asObject().getString("name", null), hackPane);
            }
        }
        catch (final IOException except) {

        }
        //TODO: Catch ParseException for invalid JSON

        final HackTreeItem subroot = new HackTreeItem(name);
        subroot.setExpanded(true);
        subroot.getChildren().addAll(hTreeItems);

        return subroot;
    }

    private class HackTreeItem extends TreeItem <String> {
        private final GridPane hackPane;

        HackTreeItem() {
            hackPane = null;
        }

        HackTreeItem(final String name) {
            //assume to be root
            super(name);
            hackPane = null;
            setExpanded(true);
        }

        HackTreeItem(final String name, final GridPane hackPane) {
            super(name);
            this.hackPane = hackPane;
        }
    }
}