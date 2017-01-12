package com.fdl.keroedit;

import java.io.File;

import javafx.application.Application;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.collections.FXCollections;

import javafx.scene.control.Alert;

import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyEvent;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;

import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;

import javafx.scene.image.ImageView;

import javafx.stage.DirectoryChooser;

import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.util.Utilities;

public class KeroEdit extends Application {
    private Stage mainStage;
    private BorderPane mainBorderPane;

    private ListView <File> mapList;

    private final static String VERSION_STRING = "0.0.1";

    private GameData gameData;

    @Override
    public void start(Stage stage) {
        mainStage = stage;

        final Rectangle2D display = Screen.getPrimary().getVisualBounds();

        mainBorderPane = new BorderPane();
        setupMenuBar();
        setupMapList();

        final Scene scene = new Scene(mainBorderPane, display.getWidth(), display.getHeight());

        stage.setScene(scene);
        stage.setTitle("KeroEdit v" + VERSION_STRING);
        stage.show();
    }

    private void setupMenuBar() {
        final MenuBar mBar = new MenuBar();
        mBar.prefWidthProperty().bind(mainStage.widthProperty());

        /* Underscores underline the following letter, allowing Alt + letter to be used as a 'hotkey'
           or mnemonic (at least on Windows) */
        final Menu[] menus = {new Menu("_File"), new Menu("_Edit"), new Menu("_Help")};
        for (Menu m : menus) {
            mBar.getMenus().add(m);
        }

        final MenuItem[][] menuItems = {{new MenuItem("_Open"), new MenuItem("_Save")},
                                        {new MenuItem("_Undo"), new MenuItem("_Redo")},
                                        {new MenuItem("_About"), new MenuItem("_Guide")}};

        for (int i = 0; i < menus.length; ++i) {
            menus[i].getItems().addAll(menuItems[i]);
        }

        //See earlier comment on underscores and mnemonics
        for (MenuItem[] mItems : menuItems) {
            for (MenuItem mItem : mItems) {
                mItem.setMnemonicParsing(true);
            }
        }

        menuItems[0][0].setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[0][0].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final DirectoryChooser dChooser = new DirectoryChooser();
                dChooser.setTitle("Browse to the Kero Blaster or Pink Hour Resource Folder (rsc_x)");
                final File resourceDirectory = dChooser.showDialog(mainStage);

                if (null != resourceDirectory) { //ensure a folder was selected
                    if (!(resourceDirectory.getName().equals("rsc_p") || resourceDirectory.getName().equals("rsc_k"))) {
                        Utilities.createErrorAlert("Invalid mod path", null, "Invalid path for mod.\nMust be either rsc_p or rsc_k.");
                        return;
                    }
                    gameData = new GameData(resourceDirectory);

                    addMapsToList();

                    for (MenuItem[] mItems : menuItems) {
                        for (MenuItem mItem : mItems) {
                            if (mItem.isDisable()) {
                                mItem.setDisable(false); //valid mod folder opened, so now allow using these options
                            }
                        }
                    }
                }
            }
        });

        menuItems[0][1].setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[0][1].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                gameData.save();
            }
        });
        menuItems[0][1].setDisable(true); //Disable until valid mod folder opened

        menuItems[1][0].setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        /*menuItems[1][0].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

            }
        });*/
        menuItems[1][0].setDisable(true);

        menuItems[1][1].setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        /*menuItems[1][1].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

            }
        });*/
        menuItems[1][1].setDisable(true);

        menuItems[2][0].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                final Alert about = new Alert(Alert.AlertType.INFORMATION);

                about.setTitle("About");
                about.setHeaderText(null);
                about.setContentText("Created by FDeityLink on 2017-01-11\nVersion v" + VERSION_STRING);
                about.getDialogPane().setGraphic(new ImageView(this.getClass().getResource("resource/fdl_logo.png").toString()));

                about.show();
            }
        });

        mainBorderPane.setTop(mBar);
    }

    /**
     * Creates the map list {@code List View} on the left,
     * but does not put any maps into it, and binds no actions to it.
     */
    private void setupMapList() {
        mapList = new ListView <File>();

        mapList.setPrefWidth(150);
        mapList.setPrefHeight(700);
        mapList.setOrientation(Orientation.VERTICAL);

        mainBorderPane.setLeft(mapList);
    }

    /**
     * Adds all the maps to the List View that was previously created and binds actions to it
     */
    private void addMapsToList() {
        mapList.setItems(FXCollections.observableArrayList(gameData.getMapList()));
        mapList.setCellFactory(mapListView -> new ListCell <File>() {
            @Override
            public void updateItem(File item, boolean empty) { //Uses just base filename instead of full path
                super.updateItem(item, empty);
                setText(empty ? null : item.getName().replace(".pxpack", ""));
            }
        });


        final MenuItem[] contextMenuItems = {new MenuItem("_Open Map"), new MenuItem("_New Map"),
                                             new MenuItem("_Delete Map"), new MenuItem("Duplicate _Map")};
        for (MenuItem mItem : contextMenuItems) {
            mItem.setMnemonicParsing(true);
        }

        for (MenuItem mItem : contextMenuItems) { //TODO: Replace this loop with legitimate action binding
            mItem.setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    Utilities.createInformationAlert(mItem.getText().replace("_", ""), null, "Not implemented");
                }
            });
        }

        final ContextMenu rtClickMenu = new ContextMenu(contextMenuItems);

        mapList.setOnKeyPressed(new EventHandler <KeyEvent>() { //TODO: After implementing map editor, have this open that
            @Override
            public void handle(KeyEvent event) {
                if (KeyCode.ENTER == event.getCode()) {
                    Utilities.createInformationAlert("Open map", null, "Not implemented");
                }
            }
        });

        mapList.setOnMouseClicked(new EventHandler <MouseEvent>() { //TODO: After implementing map editor, have this open that
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    Utilities.createInformationAlert("Open map", null, "Not implemented");
                }
                else if (event.getButton().equals(MouseButton.SECONDARY)) { //right-click
                    rtClickMenu.show(mainBorderPane, event.getScreenX(), event.getScreenY());
                }
            }
        });
    }

    public static void main(final String[] args) {
        launch(args);
    }
}