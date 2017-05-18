package io.fdeitylink.keroedit.hack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import java.io.IOException;

import java.io.BufferedReader;
import java.nio.charset.Charset;

import io.fdeitylink.util.fx.FileEditTab;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.scene.control.Tooltip;

import javafx.scene.control.TreeView;
import javafx.scene.control.TreeItem;

import javafx.geometry.Insets;

import javafx.scene.layout.HBox;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.Font;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.util.fx.FXUtil;

import io.fdeitylink.keroedit.gamedata.GameData;

//TODO: Singleton enum (like GameData?)
public final class HackTab extends FileEditTab {
    private static HackTab inst;

    private SplitPane sPane;

    private HackTab(final Path executable) {
        super(executable);
    }

    //TODO: don't init() until getInst(), where it is initialized if null?
    public static void init() {
        if (!GameData.INSTANCE.isInitialized()) {
            throw new IllegalStateException("Attempt to create HackTab when GameData has not been properly initialized yet");
        }

        inst = new HackTab(GameData.INSTANCE.getExecutable());

        final String stringsFname;
        switch (GameData.INSTANCE.getModType()) {
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
        Path stringsPath = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                     File.separatorChar + "assist" + File.separatorChar + stringsFname);
        if (!Files.exists(stringsPath)) {
            stringsPath = ResourceManager.getPath("assist/" + stringsFname);
            if (null == stringsPath) { //this should never happen but I'm being safe
                //inst = null;
                //TODO: throw some kind of exception...
            }
        }

        final HackTreeItem stringsTreeItem = parseHackFile(stringsPath, Messages.getString("HackTab.Roots.STRINGS"));

        inst.sPane = new SplitPane();

        final HackTreeItem root = new HackTreeItem();
        root.setExpanded(true);

        final TreeView <String> hacksTree = new TreeView <>(root);
        hacksTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isLeaf()) {
                inst.sPane.getItems().set(1, ((HackTreeItem)newValue).hackPane);
            }
        });

        hacksTree.getRoot().getChildren().add(stringsTreeItem);

        /*
         * Get first item of root, which only has children
         * and then get the first item of that "subroot",
         * which also only has children.
         */
        inst.sPane.getItems().addAll(hacksTree, ((HackTreeItem)hacksTree.getRoot().getChildren().get(0)
                                                                        .getChildren().get(0)).hackPane);
        inst.sPane.setDividerPositions(0.2);

        //TODO: Apply button

        inst.setText(Messages.getString("HackTab.TITLE"));
        inst.setTooltip(new Tooltip(GameData.INSTANCE.getExecutable().toString()));

        inst.setContent(inst.sPane);
    }

    public static void wipe() {
        inst = null;
    }

    //TODO: add isInitialized()?

    public static HackTab getInst() {
        if (null == inst) {
            throw new IllegalStateException("Attempt to get instance of HackTab when it has not been properly initialized yet");
        }
        return inst;
    }

    @Override
    public void undo() {
        //does nothing
    }

    @Override
    public void redo() {
        //does nothing
    }

    @Override
    public void save() {
        //TODO: Actually save EXE
        markUnchanged();
    }

    private static HackTreeItem parseHackFile(final Path hackPath, final String subrootName) {
        HackTreeItem[] hTreeItems = null;

        try (BufferedReader hackFileReader = Files.newBufferedReader(hackPath, Charset.forName("UTF-8"))) {
            //TODO: Be safer with parsing (e.g. check isArray(), etc. so handle malformed input)
            final JsonArray sects = Json.parse(hackFileReader).asObject().get("sects").asArray();
            hTreeItems = new HackTreeItem[sects.size()];

            int i = 0;
            for (final JsonValue sect : sects) {
                final GridPane hackPane = new GridPane(); //TODO: VBox
                hackPane.setPadding(new Insets(10, 10, 10, 10));
                hackPane.setVgap(10);
                hackPane.setHgap(10);

                int y = 0;
                for (final JsonValue item : sect.asObject().get("items").asArray()) {
                    final String label = item.asObject().getString("label", "<label missing>");
                    final String currVal = item.asObject().getString("default", ""); //change to read str from exe; strip null terminators
                    final String defVal = item.asObject().getString("default", "<default missing>");
                    final int len = item.asObject().getInt("len", -1);
                    final int offset = item.asObject().getInt("offset", -1);

                    hackPane.add(new HackField(label, currVal, defVal, len, offset), 0, y++);
                }

                hTreeItems[i++] = new HackTreeItem(sect.asObject().getString("name", null), hackPane);
            }
        }
        catch (final IOException except) {

        }
        //TODO: Catch ParseException for invalid JSON

        final HackTreeItem subroot = new HackTreeItem(subrootName);
        subroot.setExpanded(true);
        subroot.getChildren().addAll(hTreeItems);

        return subroot;
    }

    private static final class HackTreeItem extends TreeItem <String> {
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

    //TODO: Make this extend GridPane to align text and fields
    private static final class HackField extends HBox {
        private final Text label;
        private final TextField field;
        private final int offset;

        HackField(final String labelText, final String currentVal, final String defaultVal, final int len, final int offset) {
            super(10);

            this.offset = offset;

            label = new Text(labelText);
            label.setFont(Font.font(null, FontWeight.NORMAL, 12));

            field = new TextField(currentVal);
            field.setDisable(-1 == len || -1 == offset);
            field.setTooltip(new Tooltip("Default: " + defaultVal));
            FXUtil.setTextControlLength(field, len);

            getChildren().addAll(label, field);
        }
    }
}