/* TODO:
 * Add save to MapEditTab so it saves no matter what sub-tab is selected
 * Add undo/redo
 * Create UI -> application logic mediators & use javafx.concurrent
 * Comments and Javadoc
 * PxPack getters and setters
 * Make objects immutable?
 * Use switch statements where applicable
 * Make the system friendly for undo/redo (start off by being able to roll-back map deletions)
 * Allow user to drag right side of map ListView to expand/shrink it as needed to fit mapnames
 * Put enum filler into method in Utilities class
 * Think about patching method - similar to Plus Porter
 *  - Require unchanged game folder and mod folder
 *  - Find and save differences between unchanged and mod folders
 * Test equals() for MapEditTab and PxPack classes - seems fine
 */

package com.fdl.keroedit;

import java.io.File;

import java.util.Arrays;

import java.util.EnumMap;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.application.Application;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javafx.geometry.Orientation;
import javafx.collections.FXCollections;

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

import com.fdl.keroedit.map.MapEditTab;

public class KeroEdit extends Application {
    Preferences prefs;

    private Stage mainStage;
    private BorderPane mainBorderPane;
    private TabPane mainTabPane;

    private ListView <File> mapList;

    private final static String VERSION_STRING = "0.0.1";
    private final static String LAST_UPDATED_STRING = "2017-01-13";

    private GameData gameData;

    /**
     * Starts running the KeroEdit program
     *
     * @param stage The stage to display and run the program through
     */
    @Override
    public void start(final Stage stage) {
        mainStage = stage;

        loadPreferences();
        stage.setOnCloseRequest(new EventHandler <WindowEvent>() {
            @Override
            public void handle(final WindowEvent event) {
                savePreferences();
                mainStage.close();
                event.consume();
            }
        });

        mainBorderPane = new BorderPane();
        setupMenuBar();
        setupMapList();
        setupTabPane();

        Rectangle2D display = Screen.getPrimary().getVisualBounds();
        final Scene scene = new Scene(mainBorderPane, display.getWidth(), display.getHeight());

        mainStage.setScene(scene);
        mainStage.setTitle("KeroEdit v" + VERSION_STRING);
        mainStage.show();
        mainStage.requestFocus();
    }

    private void loadPreferences() { //Currently JRE fails to open prefs, fix supposed to arrive in Java 9(will it be given to earlier JREs?)
        prefs = Preferences.userNodeForPackage(this.getClass());
    }

    private void savePreferences() {
        try {
            prefs.flush();
        }
        catch (BackingStoreException except) {
            System.err.println("Failed to save preferences!\n " + except.getMessage());
        }
    }

    /**
     * Sets up the menu bar that appears at the top of the KeroEdit program
     */
    private void setupMenuBar() {
        final MenuBar mBar = new MenuBar();
        mBar.setPrefWidth(mainStage.getWidth());

        /* Underscores underline the following letter, allowing Alt + letter to be used as a 'hotkey'
           or mnemonic (at least on Windows) */
        final Menu[] menus = {new Menu("_File"), new Menu("_Edit"), new Menu("_Help")};
        for (Menu m : menus) {
            mBar.getMenus().add(m);
        }

        final MenuItem[][] menuItems = {{new MenuItem("_Open"), new MenuItem("_Save"), new MenuItem("_Reload")},
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

        final EnumMap <MenuBarMenuIndex, Integer> menuBarMenuIndexes = new EnumMap <MenuBarMenuIndex, Integer>(MenuBarMenuIndex.class);
        final EnumMap <FileMenuItemIndex, Integer> fileMenuItemIndexes = new EnumMap <FileMenuItemIndex, Integer>(FileMenuItemIndex.class);
        final EnumMap <EditMenuItemIndex, Integer> editMenuItemIndexes = new EnumMap <EditMenuItemIndex, Integer>(EditMenuItemIndex.class);
        final EnumMap <HelpMenuItemIndex, Integer> helpMenuItemIndexes = new EnumMap <HelpMenuItemIndex, Integer>(HelpMenuItemIndex.class);
        for (int i = 0; i < MenuBarMenuIndex.values().length; ++i) {
            menuBarMenuIndexes.put(MenuBarMenuIndex.values()[i], i);
        }
        for (int i = 0; i < FileMenuItemIndex.values().length; ++i) {
            fileMenuItemIndexes.put(FileMenuItemIndex.values()[i], i);
        }
        for (int i = 0; i < EditMenuItemIndex.values().length; ++i) {
            editMenuItemIndexes.put(EditMenuItemIndex.values()[i], i);
        }
        for (int i = 0; i < HelpMenuItemIndex.values().length; ++i) {
            helpMenuItemIndexes.put(HelpMenuItemIndex.values()[i], i);
        }

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_ITEM_OPEN)]
            .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_ITEM_OPEN)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    final FileChooser fChooser = new FileChooser();
                    fChooser.setTitle("Browse to a Kero Blaster or Pink Hour executable file");
                    fChooser.setInitialDirectory(new File(prefs.get(PreferencesStrings.LAST_DIRECTORY.toString(), System.getProperty("user.home"))));

                    FileChooser.ExtensionFilter[] extensionFilters = {new FileChooser.ExtensionFilter("KB/PH Executable File", "*.exe"),
                                                                      new FileChooser.ExtensionFilter("All files", "*.*")};
                    fChooser.getExtensionFilters().addAll(extensionFilters);
                    fChooser.setSelectedExtensionFilter(extensionFilters[0]);

                    final File executable = fChooser.showOpenDialog(mainStage);

                    if (null != executable) { //user didn't close dialog before selection
                        //Save last directory to preferences
                        prefs.put(PreferencesStrings.LAST_DIRECTORY.toString(), executable.getParent());

                        final File[] directories = executable.getParentFile().listFiles();
                        if (null == directories) {
                            Utilities.createErrorAlert("Invalid mod path", null,
                                                       "Could not locate rsc_p or rsc_k folder");
                            return;
                        }

                        String rscFolder = Arrays.asList(directories).contains(new File(executable.getParent() + "/rsc_p/")) ? "/rsc_p/" :
                                           Arrays.asList(directories).contains(new File(executable.getParent() + "/rsc_k/")) ? "/rsc_k/" :
                                           null;

                        if (null == rscFolder) {
                            Utilities.createErrorAlert("Invalid mod path", null,
                                                       "Could not locate rsc_p or rsc_k folder");
                            return;
                        }

                        gameData = new GameData(executable, rscFolder);
                        mainStage.setTitle("KeroEdit v" + VERSION_STRING + " - " + executable.getParent());

                        addMapsToListView();
                        bindActionsToMapListView();

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

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_ITEM_SAVE)]
            .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        /*menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_ITEM_SAVE)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {

                }
            });*/
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_ITEM_SAVE)]
            .setDisable(true); //Disable until valid mod folder opened

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_RELOAD)]
            .setAccelerator(new KeyCodeCombination(KeyCode.F5));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_RELOAD)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    mainTabPane.getTabs().clear(); //close all open tabs
                    gameData = new GameData(gameData.getExecutable(), gameData.getResourceFolder());
                    addMapsToListView();
                    //TODO: Check if mod was deleted since last load
                    //TODO: Warn about unsaved changes
                }
            });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_FILE)][fileMenuItemIndexes.get(FileMenuItemIndex.FILE_MENU_RELOAD)]
            .setDisable(true);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_EDIT)][editMenuItemIndexes.get(EditMenuItemIndex.EDIT_MENUI_TEM_UNDO)]
            .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        /*menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_EDIT)][editMenuItemIndexes.get(EditMenuItemIndex.EDIT_MENUI_TEM_UNDO)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {

                }
            });*/
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_EDIT)][editMenuItemIndexes.get(EditMenuItemIndex.EDIT_MENUI_TEM_UNDO)]
            .setDisable(true);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_EDIT)][editMenuItemIndexes.get(EditMenuItemIndex.EDIT_MENU_ITEM_REDO)]
            .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        /*menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_EDIT)][editMenuItemIndexes.get(EditMenuItemIndex.EDIT_MENU_ITEM_REDO)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {

                }
            });*/
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_EDIT)][editMenuItemIndexes.get(EditMenuItemIndex.EDIT_MENU_ITEM_REDO)]
            .setDisable(true);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_HELP)][helpMenuItemIndexes.get(HelpMenuItemIndex.HELP_MENU_ITEM_ABOUT)]
            .setOnAction(new EventHandler <ActionEvent>() {
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

        /*menuItems[menuBarMenuIndexes.get(MenuBarMenuIndex.MENU_HELP)][helpMenuItemIndexes.get(HelpMenuItemIndex.HELP_MENU_ITEM_GUIDE)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {

                }
            });*/

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
     * Adds all the maps to the {@code ListView} for maps that was previously created by {@code setupMapList()}
     */
    private void addMapsToListView() {
        mapList.setItems(FXCollections.observableArrayList(gameData.getMapList()));
        mapList.setCellFactory(new Callback <ListView <File>, ListCell <File>>() {
            @Override
            public ListCell <File> call(final ListView <File> listView) {
                return new ListCell <File>() {
                    @Override
                    public void updateItem (File item,boolean empty){ //Uses just base filename instead of full path
                        super.updateItem(item, empty);
                        setText(empty ? null : item.getName().replace(".pxpack", ""));
                    }
                };
            }
        });
    }

    /**
     * Binds actions to the {@code ListView} for maps that was previously created by {@code setupMapList()}
     */
    private void bindActionsToMapListView() {
        final MenuItem[] contextMenuActions = {new MenuItem("_Open Map"), new MenuItem("_New Map"),
                                               new MenuItem("_Delete Map"), new MenuItem("Duplicate _Map"),
                                               new MenuItem("_Rename Map")};

        final ContextMenu contextMenu = new ContextMenu(contextMenuActions);

        for (MenuItem mItem : contextMenuActions) {
            mItem.setMnemonicParsing(true);
        }

        final EnumMap <MapListContextMenuItems, Integer> mapListContextMenuItemIndexes
            = new EnumMap <MapListContextMenuItems, Integer>(MapListContextMenuItems.class);
        for (int i = 0; i < MapListContextMenuItems.values().length; ++i) {
            mapListContextMenuItemIndexes.put(MapListContextMenuItems.values()[i], i);
        }

        /* Accelerators present more so to show key mappings, as the accelerators not triggered
         * (probably since they're in a context menu). So I have the mapList.setOnKeyPressed
         * thing below which has the same keys mapped
         */
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_OPEN)]
            .setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_OPEN)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    MapEditTab mEdit = new MapEditTab(mapList.getSelectionModel().getSelectedItem());

                    if (mainTabPane.getTabs().contains(mEdit)) { //Tab for this file already exists
                        mainTabPane.getSelectionModel().select(mEdit);
                        return;
                    }

                    mainTabPane.getTabs().add(mEdit);
                    mainTabPane.getSelectionModel().select(mEdit);
                }
            });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_NEW)]
            .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_NEW)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Utilities.createInformationAlert("New Map", null, "Not implemented");
                    //TODO: When I implement this, put it into other thread
                    /*File f;
                    for (int i = 0; ; ++i) {
                        f = new File(gameData.getExecutable().getParent() + gameData.getResourceFolder());
                        if (!f.exists()) {
                            //create a PxPackMap object with f as parameter
                        }
                    }*/
                }
            });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_DELETE)]
            .setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_DELETE)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
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

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_DUPLICATE)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Utilities.createInformationAlert("Duplicate Map", null, "Not implemented");
                    //TODO: Create prompt for new mapname
                }
            });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_RENAME)]
            .setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Utilities.createInformationAlert("Rename Map", null, "Not implemented");
                }
            });

        mapList.setOnKeyPressed(new EventHandler <KeyEvent>() {
            @Override
            public void handle(final KeyEvent event) {
                int menuItemIndex = event.getCode().equals(KeyCode.ENTER) ?
                    mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_OPEN) :
                    event.getCode().equals(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN).getCode()) ?
                            mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_NEW) :
                            event.getCode().equals(KeyCode.DELETE) ?
                                mapListContextMenuItemIndexes.get(MapListContextMenuItems.MAP_LIST_CONTEXT_MENU_ITEM_DELETE) : -1;

                if (-1 != menuItemIndex) {
                    contextMenu.getItems().get(menuItemIndex).getOnAction().handle(new ActionEvent());
                }
            }
        });

        mapList.setOnMouseClicked(new EventHandler <MouseEvent>() {
            @Override
            public void handle(final MouseEvent event) {
                if (event.getClickCount() == 2) {
                    contextMenu.getItems().get(0).getOnAction().handle(new ActionEvent());
                }
                else if (event.getButton().equals(MouseButton.SECONDARY)) { //right-click
                    contextMenu.show(mainBorderPane, event.getScreenX(), event.getScreenY());
                }
            }
        });
    }

    /**
     * Sets up the {@code TabPane} that forms the core of the KeroEdit program
     */
    private void setupTabPane() {
        mainTabPane = new TabPane();

        mainTabPane.setPrefHeight(mainStage.getHeight());
        mainTabPane.setPrefWidth(mainStage.getWidth());

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);
        Utilities.addCtrlWClose(mainTabPane);

        mainBorderPane.setCenter(mainTabPane);
    }

    public static void main(final String[] args) {
        launch(args);
    }

    private enum MenuBarMenuIndex {
        MENU_FILE,
        MENU_EDIT,
        MENU_HELP
    }
    private enum FileMenuItemIndex {
        FILE_MENU_ITEM_OPEN,
        FILE_MENU_ITEM_SAVE,
        FILE_MENU_RELOAD
    }
    private enum EditMenuItemIndex {
        EDIT_MENUI_TEM_UNDO,
        EDIT_MENU_ITEM_REDO
    }
    private enum HelpMenuItemIndex {
        HELP_MENU_ITEM_ABOUT,
        HELP_MENU_ITEM_GUIDE
    }

    private enum MapListContextMenuItems {
        MAP_LIST_CONTEXT_MENU_ITEM_OPEN,
        MAP_LIST_CONTEXT_MENU_ITEM_NEW,
        MAP_LIST_CONTEXT_MENU_ITEM_DELETE,
        MAP_LIST_CONTEXT_MENU_ITEM_DUPLICATE,
        MAP_LIST_CONTEXT_MENU_ITEM_RENAME
    }

    private enum PreferencesStrings {
        LAST_DIRECTORY
    }
}