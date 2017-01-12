//TODO: Check Java version compatibility
//TODO: Think about patching method - similar to Plus Porter
//TODO: Use switch statements where applicable
//TODO: Implement equals() for MapEdit?
//TODO: Put repetitive code into private methods
//TODO: Make the system friendly for undo/redo (start off by being abl to rollback deletions

package com.fdl.keroedit;

import java.io.File;

import java.util.Arrays;

import javafx.application.Application;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import javafx.scene.control.ListView;
import javafx.geometry.Orientation;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;

import javafx.scene.control.Alert;

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;

import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyEvent;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;

import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;

import javafx.scene.image.ImageView;

import javafx.stage.FileChooser;

import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.map.MapEdit;

import com.fdl.keroedit.util.Utilities;

public class KeroEdit extends Application {
    private Stage mainStage;
    private BorderPane mainBorderPane;
    private TabPane mainTabPane;

    private ListView <File> mapList;

    private final static String VERSION_STRING = "0.0.1";
    private final static String LAST_UPDATED_STRING = "2017-01-12";

    private GameData gameData;

    @Override
    public void start(Stage stage) {
        mainStage = stage;

        mainBorderPane = new BorderPane();
        setupMenuBar();
        setupMapList();
        setupTabPane();

        Rectangle2D display = Screen.getPrimary().getVisualBounds();
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
            public void handle(final ActionEvent event) {
                final FileChooser fChooser = new FileChooser();
                fChooser.setTitle("Browse to a Kero Blaster or Pink Hour executable file");
                fChooser.setInitialDirectory(new File(System.getProperty("user.home")));

                FileChooser.ExtensionFilter[] extensionFilters = {new FileChooser.ExtensionFilter("KB/PH Executable File", "*.exe"),
                                                                  new FileChooser.ExtensionFilter("All files", "*.*")};
                fChooser.getExtensionFilters().addAll(extensionFilters);
                fChooser.setSelectedExtensionFilter(extensionFilters[0]);

                final File executable = fChooser.showOpenDialog(mainStage);

                if (null != executable) { //user didn't close dialog before selection
                    final File[] directories = executable.getParentFile().listFiles();
                    if (null == directories) {
                        Utilities.createErrorAlert("Invalid mod path", null,
                                                   "Could not locate rsc_p or rsc_k folder");
                        return;
                    }

                    Arrays.sort(directories);

                    String resourceFolder = (0 <= Arrays.binarySearch(directories, new File(executable.getParent() + "/rsc_p"))) ?
                                            "/rsc_p" : (0 <= Arrays.binarySearch(directories, new File(executable.getParent() + "/rsc_k/"))) ?
                                                        "/rsc_k/" : null;

                    if (null == resourceFolder) {
                        Utilities.createErrorAlert("Invalid mod path", null,
                                                   "Could not locate rsc_p or rsc_k folder");
                        return;
                    }

                    gameData = new GameData(executable, resourceFolder);
                    mainStage.setTitle("KeroEdit v" + VERSION_STRING + " - " + executable.getParent());

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

        menuItems[0][1].setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        /*menuItems[0][1].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

            }
        });*/
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
            public void handle(final ActionEvent event) {
                final Alert about = new Alert(Alert.AlertType.INFORMATION);

                about.setTitle("About");
                about.setHeaderText(null);
                about.setContentText("Created by FDeityLink on " + LAST_UPDATED_STRING + "\nVersion v" + VERSION_STRING);
                about.getDialogPane().setGraphic(new ImageView(this.getClass().getResource("/com/fdl/keroedit/resource/fdl_logo.png").toString()));

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

        mapList.setPrefHeight(mainStage.getHeight());
        mapList.setPrefWidth(125); //TODO: Variable size this
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

        final MenuItem[] mapListActions = {new MenuItem("_Open Map"), new MenuItem("_New Map"),
                                           new MenuItem("_Delete Map"), new MenuItem("Duplicate _Map"),
                                           new MenuItem("_Rename Map")};
        for (MenuItem mItem : mapListActions) {
            mItem.setMnemonicParsing(true);
        }

        /* Accelerators present more so to show key mappings, as the accelerators not triggered
         * (probably since they're in a context menu). So I have the mapList.setOnKeyPressed
         * thing below which has the same keys mapped
         */
        mapListActions[0].setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        mapListActions[0].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(final ActionEvent event) {
                for (Tab tab : mainTabPane.getTabs()) {
                    if (tab.getText().equals(mapList.getSelectionModel().getSelectedItem().getName().replace(".pxpack", ""))) {
                        //Not the best way to check but it works
                        mainTabPane.getSelectionModel().select(tab); //select already open tab
                        return;
                    }
                }

                MapEdit mEdit = new MapEdit(mapList.getSelectionModel().getSelectedItem());
                mainTabPane.getTabs().add(mEdit);
                mainTabPane.getSelectionModel().select(mEdit);
            }
        });

        mapListActions[1].setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        mapListActions[1].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Utilities.createInformationAlert("New Map", null, "Not implemented");

                /*File f;
                for (int i = 0; ; ++i) {
                    f = new File(gameData.getExecutable().getParent() + gameData.getResourceFolder());
                    if (!f.exists()) {
                        //create a PxPackMap object with f as parameter
                    }
                }*/
            }
        });

        mapListActions[2].setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        mapListActions[2].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                File map = mapList.getSelectionModel().getSelectedItem();
                gameData.removeMap(map);
                mapList.getItems().remove(map);

                for (Tab tab : mainTabPane.getTabs()) {
                    if (tab.getText().equals(map.getName().replace(".pxpack", ""))) {
                        mainTabPane.getTabs().remove(tab);
                        break;
                    }
                }
            }
        });

        mapListActions[3].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Utilities.createInformationAlert("Duplicate Map", null, "Not implemented");
                //TODO: Create prompt for new mapname
            }
        });

        mapListActions[4].setOnAction(new EventHandler <ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Utilities.createInformationAlert("Rename Map", null, "Not implemented");
            }
        });

        final ContextMenu rtClickMenu = new ContextMenu(mapListActions);

        mapList.setOnKeyPressed(new EventHandler <KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode().equals(KeyCode.ENTER)) {
                    for (Tab tab : mainTabPane.getTabs()) {
                        if (tab.getText().equals(mapList.getSelectionModel().getSelectedItem().getName().replace(".pxpack", ""))) {
                            //Not the best way to check but it works
                            mainTabPane.getSelectionModel().select(tab); //select already open tab
                            return;
                        }
                    }

                    MapEdit mEdit = new MapEdit(mapList.getSelectionModel().getSelectedItem());
                    mainTabPane.getTabs().add(mEdit);
                    mainTabPane.getSelectionModel().select(mEdit);
                }
                else if (event.getCode().equals(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN).getCode())) {
                    Utilities.createInformationAlert("New Map", null, "Not implemented");
                }
                else if (event.getCode().equals(KeyCode.DELETE)) {
                    File map = mapList.getSelectionModel().getSelectedItem();
                    gameData.removeMap(map);
                    mapList.getItems().remove(map);
                    for (Tab tab : mainTabPane.getTabs()) {
                        if (tab.getText().equals(map.getName().replace(".pxpack", ""))) {
                            mainTabPane.getTabs().remove(tab);
                            break;
                        }
                    }
                }
            }
        });

        mapList.setOnMouseClicked(new EventHandler <MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    for (Tab tab : mainTabPane.getTabs()) {
                        if (tab.getText().equals(mapList.getSelectionModel().getSelectedItem().getName().replace(".pxpack", ""))) {
                            //Not the best way to check but it works
                            mainTabPane.getSelectionModel().select(tab); //select already open tab
                            return;
                        }
                    }

                    MapEdit mEdit = new MapEdit(mapList.getSelectionModel().getSelectedItem());
                    mainTabPane.getTabs().add(mEdit);
                    mainTabPane.getSelectionModel().select(mEdit);
                }
                else if (event.getButton().equals(MouseButton.SECONDARY)) { //right-click
                    rtClickMenu.show(mainBorderPane, event.getScreenX(), event.getScreenY());
                }
            }
        });
    }

    private void setupTabPane() {
        mainTabPane = new TabPane();

        mainTabPane.setPrefHeight(mainStage.getHeight());
        mainTabPane.setPrefWidth(mainStage.getWidth());

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);
        Utilities.addControlWClose(mainTabPane);

        mainBorderPane.setCenter(mainTabPane);
    }

    public static void main(final String[] args) {
        launch(args);
    }
}