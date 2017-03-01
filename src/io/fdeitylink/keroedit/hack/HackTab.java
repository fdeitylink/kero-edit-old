package io.fdeitylink.keroedit.hack;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import io.fdeitylink.keroedit.Messages;
import io.fdeitylink.keroedit.resource.ResourceManager;
import io.fdeitylink.keroedit.util.FileEditTab;
import io.fdeitylink.keroedit.util.JavaFXUtil;
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

import io.fdeitylink.keroedit.gamedata.GameData;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;

public class HackTab extends FileEditTab {
    private final SplitPane sPane;

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

        final TreeView <String> hacksTree = new TreeView <>(root);
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

    }

    private HackTreeItem parseHackFile(final File hackFile, final String name) {
        FileReader hackFileReader = null;
        HackTreeItem[] hTreeItems = null;

        try {
            final JsonArray sects = Json.parse(hackFileReader = new FileReader(hackFile)).asObject().get("sects").asArray();
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
        finally {
            try {
                if (null != hackFileReader) {
                    hackFileReader.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        final HackTreeItem subroot = new HackTreeItem(name);
        subroot.setExpanded(true);
        subroot.getChildren().addAll(hTreeItems);

        return subroot;
    }

    private static class HackTreeItem extends TreeItem <String> {
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

    private static class HackField extends HBox {
        private final Text label;
        private final TextField field;
        private final int offset;

        HackField(final String labelText, final String currentVal, final String defaultVal, final int len, final int offset) {
            //TODO: align text and fields in rows and columns (have this extend GridPane?
            super(10);

            this.offset = offset;

            label = new Text(labelText);
            label.setFont(Font.font(null, FontWeight.NORMAL, 12));

            field = new TextField(currentVal);
            field.setDisable(-1 == len || -1 == offset);
            field.setTooltip(new Tooltip("Default: " + defaultVal));
            JavaFXUtil.setTextFieldLength(field, len);

            getChildren().addAll(label, field);
        }
    }
}