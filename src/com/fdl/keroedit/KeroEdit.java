/*
 * TODO:
 * Add save to MapEditTab so it saves no matter what subtab is selected
 * Use javafx.concurrent
 *  - Synchronize values
 *  - Use Tasks
 * Use switch statements where applicable
 * Make the system friendly for undo/redo
 *  - Undo map delete
 *  - Global undo/redo Stacks storing Methods (or will everything that implements Changeable have its own stack?)
 *     - Undoing/redoing should set the focus onto the relevant tab
 *     - On tab close, remove relevant undo/redos from Stack (make a special Tab class to inherit from)
 * Put enum filler into method in util package
 * Use Tab.setOnCloseRequest() to warn about unsaved changes
 * Resort the map ListView alphabetically when a map is added, and select and open the new map
 * Additional menubar items
 *  - Actions menu for general stuffs
 *  - Open PXEVE not from map (such as an unused one or explain.pxeve)
 *     - Config option for english vs jp
 * Use a build system (Maven, Ant, or Gradle?)
 * Read up on the manifest system and see if there's anything you need to or could change
 * Put equivalent parts of open, load last, and reload into one method (Task?) to call in all of their handlers
 * Make members that should logically be final final (use temp variables to set?)
 * Draggable tabs (and allow popping out into a window)
 * Bind actions to maplist in initMapList()?
 * Pass object constructors Files still or move to strings relative to GameData?
 * Use nio.Files and nio.Path instead of io.File?
 * Allow opening multiple maps at once
 * Allow configuring tile size
 * Use iterators where possible
 * Investigate tab-related exceptions, slowness, and NoClassDefFoundError exception when trying to save prefs
 * Log runtime/uncaught exceptions
 * Scaling map down
 * Shorten tertiary statements
 * Lower memory usage and stuffs
 *
 * Make PxPack interior classes immutable?
 * Create missing directories rather than throw error?
 * Make mapListView a ListView of PxPack objects?
 * Find OS-dependent stylesheets?
 *
 *  Think about patching method - similar to Plus Porter
 *   - Require unmodified game folder and mod folder
 *   - Find and save differences between unchanged and mod folders
 *  Update downloader?
 */

package com.fdl.keroedit;

import java.io.File;

import java.nio.file.NoSuchFileException;

import java.util.EnumMap;

import java.text.MessageFormat;

import javafx.application.Application;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ColorPicker;

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

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import com.fdl.keroedit.util.JavaFXUtil;

import com.fdl.keroedit.resource.ResourceManager;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.mapedit.MapEditTab;

public class KeroEdit extends Application {
    private final static String VERSION_STRING = Messages.getString("KeroEdit.VERSION");
    private final static String LAST_UPDATED_STRING = Messages.getString("KeroEdit.LAST_UPDATE");

    private ExecutorService mainLogicExecutorService;
    private Phaser mainLogicExecutorPhaser;

    private Stage mainStage;
    private BorderPane mainBorderPane;
    private TabPane mainTabPane;

    private ListView <File> mapList;

    public static void main(final String[] args) {
        launch(args);
    }

    /**
     * Starts running the Kero Edit program
     *
     * @param stage The stage to run the Kero Edit program in
     */
    @Override
    public void start(final Stage stage) {
        mainLogicExecutorService = Executors.newSingleThreadExecutor();
        mainLogicExecutorPhaser = new Phaser();

        loadPreferences();

        initStage(stage);
    }

    private void loadPreferences() {
        mainLogicExecutorPhaser.register();

        mainLogicExecutorService.execute(new Task <Void>() {
            @Override
            protected Void call() {
                mainLogicExecutorPhaser.arriveAndAwaitAdvance();
                Config.loadPreferences();
                mainLogicExecutorPhaser.arriveAndDeregister();
                return null;
            }
        });
    }

    private void savePreferences() {
        mainLogicExecutorPhaser.register();

        mainLogicExecutorService.execute(new Task <Void>() {
            @Override
            protected Void call() {
                mainLogicExecutorPhaser.arriveAndAwaitAdvance();
                Config.savePreferences();
                mainLogicExecutorPhaser.arriveAndDeregister();
                return null;
            }
        });
    }

    /**
     * Sets up the entire stage and window, including setting up its
     * components by calling the other init methods
     *
     * @param stage The stage to run the Kero Edit program in
     */
    private void initStage(Stage stage) {
        mainStage = stage;

        mainStage.setOnCloseRequest(new EventHandler <WindowEvent>() {
            @Override
            public void handle(final WindowEvent event) {
                savePreferences();

                mainLogicExecutorService.shutdown();
                mainStage.close();
                event.consume();
                //TODO: Warn about unsaved changes
            }
        });

        mainBorderPane = new BorderPane();

        mainLogicExecutorPhaser.register();
        initMenuBar();

        initMapList();
        initTabPane();

        Rectangle2D display = Screen.getPrimary().getVisualBounds();
        final Scene scene = new Scene(mainBorderPane, display.getWidth(), display.getHeight());

        mainStage.setScene(scene);
        mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.APP_TITLE"), VERSION_STRING));
        mainStage.show();
        mainStage.requestFocus();
    }

    /**
     * Sets up the menu bar that appears at the top
     */
    private void initMenuBar() {
        final MenuBar mBar = new MenuBar();
        mBar.setPrefWidth(mainStage.getWidth());

        /*
         * Underscores underline the following letter, allowing Alt + letter to be used as a 'hotkey'
         * or mnemonic (at least on Windows)
         */
        final Menu[] menus = {new Menu(Messages.getString("KeroEdit.FILE_MENU")),
                              new Menu(Messages.getString("KeroEdit.EDIT_MENU")),
                              new Menu(Messages.getString("KeroEdit.VIEW_MENU")),
                              new Menu(Messages.getString("KeroEdit.HELP_MENU"))};
        for (final Menu m : menus) {
            mBar.getMenus().add(m);
        }

        final MenuItem[][] menuItems = {{new MenuItem(Messages.getString("KeroEdit.FileMenu.OPEN")),
                                         new MenuItem(Messages.getString("KeroEdit.FileMenu.OPEN_LAST")),
                                         new MenuItem(Messages.getString("KeroEdit.FileMenu.SAVE")),
                                         new MenuItem(Messages.getString("KeroEdit.FileMenu.SAVE_ALL")),
                                         new MenuItem(Messages.getString("KeroEdit.FileMenu.RELOAD")),
                                         new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_TAB")),
                                         new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_ALL_TABS"))},

                                        {new MenuItem(Messages.getString("KeroEdit.EditMenu.UNDO")),
                                         new MenuItem(Messages.getString("KeroEdit.EditMenu.REDO"))},

                                        {new Menu(Messages.getString("KeroEdit.ViewMenu.MAP_ZOOM")),
                                         new Menu(Messages.getString("KeroEdit.ViewMenu.TILESET_ZOOM")),
                                         new MenuItem(Messages.getString("KeroEdit.ViewMenu.TILESET_BG_COLOR"))},

                                        {new MenuItem(Messages.getString("KeroEdit.HelpMenu.ABOUT")),
                                         new MenuItem(Messages.getString("KeroEdit.HelpMenu.GUIDE"))}};

        for (int i = 0; i < menus.length; ++i) {
            menus[i].getItems().addAll(menuItems[i]);
        }

        //TODO: Put into task
        final EnumMap <MenuBarMenuIndexes, Integer> menuBarMenuIndexes = new EnumMap <MenuBarMenuIndexes, Integer>(MenuBarMenuIndexes.class);
        final EnumMap <FileMenuItemIndexes, Integer> fileMenuItemIndexes = new EnumMap <FileMenuItemIndexes, Integer>(FileMenuItemIndexes.class);
        final EnumMap <EditMenuItemIndexes, Integer> editMenuItemIndexes = new EnumMap <EditMenuItemIndexes, Integer>(EditMenuItemIndexes.class);
        final EnumMap <ViewMenuItemIndexes, Integer> viewMenuItemIndexes = new EnumMap <ViewMenuItemIndexes, Integer>(ViewMenuItemIndexes.class);
        final EnumMap <HelpMenuItemIndexes, Integer> helpMenuItemIndexes = new EnumMap <HelpMenuItemIndexes, Integer>(HelpMenuItemIndexes.class);

        int i = 0;
        for (final MenuBarMenuIndexes x : MenuBarMenuIndexes.values()) {
            menuBarMenuIndexes.put(x, i++);
        }
        i = 0;
        for (final FileMenuItemIndexes x : FileMenuItemIndexes.values()) {
            fileMenuItemIndexes.put(x, i++);
        }
        i = 0;
        for (final EditMenuItemIndexes x : EditMenuItemIndexes.values()) {
            editMenuItemIndexes.put(x, i++);
        }
        i = 0;
        for (final ViewMenuItemIndexes x : ViewMenuItemIndexes.values()) {
            viewMenuItemIndexes.put(x, i++);
        }
        i = 0;
        for (final HelpMenuItemIndexes x : HelpMenuItemIndexes.values()) {
            helpMenuItemIndexes.put(x, i++);
        }

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.OPEN)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final FileChooser fChooser = new FileChooser();
                        fChooser.setTitle(Messages.getString("KeroEdit.OpenFile.TITLE"));

                        final String initDir = Config.lastExeLoc.substring(0, Config.lastExeLoc.lastIndexOf(File.separatorChar) + 1);
                        fChooser.setInitialDirectory(new File(initDir));

                        FileChooser.ExtensionFilter[] extensionFilters =
                                {new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.OpenFile.EXECUTABLE_FILTER"),
                                                                 "*.exe"),
                                 new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.OpenFile.NO_FILTER"),
                                                                 "*.*")};
                        fChooser.getExtensionFilters().addAll(extensionFilters);
                        fChooser.setSelectedExtensionFilter(extensionFilters[0]);

                        final File executable = fChooser.showOpenDialog(mainStage);

                        mainTabPane.getTabs().clear();
                        mapList.getItems().clear();

                        //TODO: Put into Task
                        if (null != executable) { //user didn't close dialog before selection
                            try {
                                //TODO: check extension?
                                GameData.initGameData(executable);

                                Config.lastExeLoc = executable.getAbsolutePath();

                                mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.OpenFile.NEW_APP_TITLE"),
                                                                        VERSION_STRING, executable.getParent() +
                                                                                        File.separatorChar));

                                loadMapList();
                                //TODO: Check if actions already bound?
                                bindMapListViewActions();

                                for (final MenuItem[] mItems : menuItems) { //Valid mod folder opened, so now allow using these options
                                    for (final MenuItem mItem : mItems) {
                                        if (mItem.isDisable()) {
                                            mItem.setDisable(false);
                                        }
                                    }
                                }
                            }
                            catch (final NoSuchFileException except) {
                                JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("KeroEdit.OpenFile.InvalidPath.TITLE"),
                                                       null,
                                                       Messages.getString("KeroEdit.OpenFile.InvalidPath.MESSAGE"))
                                          .showAndWait();
                            }
                        }
                    }
                });

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.OPEN_LAST)]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.OPEN_LAST)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        //TODO: Put into Task
                        mainTabPane.getTabs().clear();
                        mapList.getItems().clear();

                        try {
                            final String lastExeLoc = Config.lastExeLoc;

                            GameData.initGameData(new File(Config.lastExeLoc));

                            mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.OpenFile.NEW_APP_TITLE"),
                                                                    VERSION_STRING,
                                                                    lastExeLoc.substring(0, lastExeLoc
                                                                                                    .lastIndexOf(File.separatorChar)
                                                                                            + 1)));

                            loadMapList();
                            bindMapListViewActions();

                            for (final MenuItem[] mItems : menuItems) { //Valid mod folder opened, so now allow using these options
                                for (final MenuItem mItem : mItems) {
                                    if (mItem.isDisable()) {
                                        mItem.setDisable(false);
                                    }
                                }
                            }
                        }
                        catch (final NoSuchFileException except) { //this should never happen as the executable in GameData is checked for validity on first set
                            JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("KeroEdit.OpenFile.InvalidPath.TITLE"),
                                                   null,
                                                   Messages.getString("KeroEdit.OpenFile.InvalidPath.MESSAGE"))
                                      .showAndWait();
                        }
                    }
                });

        mainLogicExecutorPhaser.arriveAndAwaitAdvance();
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.OPEN_LAST)]
                .setDisable(Config.lastExeLoc.equals(System.getProperty("user.dir")) || !Config.lastExeLoc.endsWith(".exe")); //Disabled if there is no last mod
        mainLogicExecutorPhaser.arriveAndDeregister();

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.SAVE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.SAVE)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.FileMenu.SAVE").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.SAVE)]
                .setDisable(true);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.SAVE_ALL)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.SAVE_ALL)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.FileMenu.SAVE_ALL").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.SAVE_ALL)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.RELOAD)]
                .setAccelerator(new KeyCodeCombination(KeyCode.F5));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.RELOAD)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    //TODO: Put into Task? (probably not)

                    @Override
                    public void handle(final ActionEvent event) {
                        mainTabPane.getTabs().clear(); //close all open tabs
                        mapList.getItems().clear();

                        try {
                            GameData.initGameData(GameData.getExecutable());
                            loadMapList();
                        }
                        catch (final NoSuchFileException except) { //this should never happen as the executable in GameData is checked for validity on first set
                            JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("KeroEdit.OpenFile.InvalidPath.TITLE"),
                                                   null,
                                                   Messages.getString("KeroEdit.OpenFile.InvalidPath.MESSAGE"))
                                      .showAndWait();
                        }
                        //TODO: Check if mod was deleted or modified since last load
                        //TODO: Warn about unsaved changes
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.RELOAD)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.CLOSE_TAB)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.CLOSE_TAB)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
                        if (-1 != tabIndex) {
                            mainTabPane.getTabs().remove(tabIndex);
                        }
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.CLOSE_TAB)]
                .setDisable(true);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.CLOSE_ALL_TABS)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.CLOSE_ALL_TABS)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        mainTabPane.getTabs().clear();
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.FILE)][fileMenuItemIndexes.get(FileMenuItemIndexes.CLOSE_ALL_TABS)]
                .setDisable(true);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.EDIT)][editMenuItemIndexes.get(EditMenuItemIndexes.UNDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.EDIT)][editMenuItemIndexes.get(EditMenuItemIndexes.UNDO)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.EditMenu.UNDO").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.EDIT)][editMenuItemIndexes.get(EditMenuItemIndexes.UNDO)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.EDIT)][editMenuItemIndexes.get(EditMenuItemIndexes.REDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.EDIT)][editMenuItemIndexes.get(EditMenuItemIndexes.REDO)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.EditMenu.REDO").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.EDIT)][editMenuItemIndexes.get(EditMenuItemIndexes.REDO)]
                .setDisable(true); //Disable until valid mod opened

        final RadioMenuItem[] mapZoomMenuItems = createZoomMenu(Config.mapZoom);
        for (i = 0; i < mapZoomMenuItems.length; ++i) {
            final int zoom = i + 1;
            mapZoomMenuItems[i].setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Config.mapZoom = zoom;
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab.getClass().equals(MapEditTab.class)) {
                            final MapEditTab mEditTab = (MapEditTab)tab;
                            mEditTab.setMapZoom(zoom);
                        }
                    }
                }
            });
        }
        final Menu mapZoomSubMenu = (Menu)menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.VIEW)]
                [viewMenuItemIndexes.get(ViewMenuItemIndexes.MAP_ZOOM)];
        mapZoomSubMenu.getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = createZoomMenu(Config.tilesetZoom);
        for (i = 0; i < tilesetZoomMenuItems.length; ++i) {
            final int zoom = i + 1;
            tilesetZoomMenuItems[i].setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Config.tilesetZoom = zoom;
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab.getClass().equals(MapEditTab.class)) {
                            final MapEditTab mEditTab = (MapEditTab)tab;
                            mEditTab.setTilesetZoom(zoom);
                        }
                    }
                }
            });
        }
        final Menu tilesetZoomSubMenu = (Menu)menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.VIEW)]
                [viewMenuItemIndexes.get(ViewMenuItemIndexes.TILESET_ZOOM)];
        tilesetZoomSubMenu.getItems().addAll(tilesetZoomMenuItems);

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.VIEW)][viewMenuItemIndexes.get(ViewMenuItemIndexes.TILESET_BG_COLOR)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final ColorPicker cPicker = new ColorPicker(Config.tilesetBgColor);
                        cPicker.setOnAction(new EventHandler <ActionEvent>() {
                            @Override
                            public void handle(final ActionEvent event) {
                                Config.tilesetBgColor = cPicker.getValue();
                                for (final Tab tab : mainTabPane.getTabs()) {
                                    if (tab.getClass().equals(MapEditTab.class)) {
                                        final MapEditTab mEditTab = (MapEditTab)tab;
                                        mEditTab.setTilesetBgColor(cPicker.getValue());
                                    }
                                }
                            }
                        });

                        final Dialog <Void> cPickerDialog = new Dialog <Void>();
                        cPickerDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                        cPickerDialog.getDialogPane().setContent(cPicker);
                        cPickerDialog.showAndWait();
                    }
                });

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.HELP)][helpMenuItemIndexes.get(HelpMenuItemIndexes.ABOUT)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final Alert about = JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                                   Messages.getString("KeroEdit.HelpMenu.About.TITLE"),
                                                                   null,
                                                                   MessageFormat.format(Messages.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                                        LAST_UPDATED_STRING, VERSION_STRING));

                        about.getDialogPane().setGraphic(new ImageView(ResourceManager.getImage("fdl_logo.png")));
                        about.showAndWait();
                    }
                });

        menuItems[menuBarMenuIndexes.get(MenuBarMenuIndexes.HELP)][helpMenuItemIndexes.get(HelpMenuItemIndexes.GUIDE)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.HelpMenu.GUIDE").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });

        mainBorderPane.setTop(mBar);
    }

    private RadioMenuItem[] createZoomMenu(final int currentZoom) {
        final ToggleGroup zoomToggleGroup = new ToggleGroup();
        final RadioMenuItem[] zoomMenuItems = new RadioMenuItem[5];
        for (int i = 0; i < zoomMenuItems.length; ++i) {
            zoomMenuItems[i] = new RadioMenuItem(String.valueOf((i + 1) * 100) + '%');

            zoomMenuItems[i].setToggleGroup(zoomToggleGroup);
            if (currentZoom == (i + 1)) {
                zoomMenuItems[i].setSelected(true);
            }
        }

        return zoomMenuItems;
    }

    /**
     * Creates the map list {@code List View} on the left,
     * but does not put any maps into it, and binds no actions to it.
     */
    private void initMapList() {
        mapList = new ListView <File>();

        mapList.setPrefHeight(mainStage.getHeight());
        mapList.setPrefWidth(125); //TODO: Variable size this
        mapList.setMaxWidth(250);

        mapList.setOrientation(Orientation.VERTICAL);

        mapList.setCellFactory(new Callback <ListView <File>, ListCell <File>>() {
            @Override
            public ListCell <File> call(final ListView <File> listView) {
                return new ListCell <File>() {
                    @Override
                    public void updateItem(final File item, final boolean empty) { //Uses just base filename instead of full path
                        super.updateItem(item, empty);
                        setText(empty ? null : item.getName().replace(".pxpack", ""));
                    }
                };
            }
        });

        mapList.setOnMouseDragged(new EventHandler <MouseEvent>() { //TODO: Make this work less erratically
            double prevX;

            @Override
            public void handle(final MouseEvent event) {
                mapList.setPrefWidth(mapList.getPrefWidth() + event.getSceneX() - prevX);
                prevX = event.getSceneX();
            }
        });

        mainBorderPane.setLeft(mapList);
    }

    /**
     * Adds all the maps to the {@code ListView} for maps that was previously created by {@code setupMapList()}
     */
    private void loadMapList() {
        mapList.setItems(FXCollections.observableArrayList(GameData.getMapList()));
        mapList.requestFocus();
    }

    /**
     * Binds actions to the {@code ListView} for maps that was previously created by {@code setupMapList()}
     */
    private void bindMapListViewActions() {
        final MenuItem[] contextMenuActions = {new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.OPEN_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DELETE_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP"))};
        //TODO: Add save map to context menu?

        final ContextMenu contextMenu = new ContextMenu(contextMenuActions);

        final EnumMap <MapListContextMenuItemIndexes, Integer> mapListContextMenuItemIndexes
                = new EnumMap <MapListContextMenuItemIndexes, Integer>(MapListContextMenuItemIndexes.class);
        int i = 0;
        for (final MapListContextMenuItemIndexes x : MapListContextMenuItemIndexes.values()) {
            mapListContextMenuItemIndexes.put(x, i++);
        }

        /*
         * Accelerators present more so to show key mappings, as the accelerators are not triggered
         * (probably since they're in a context menu). So I have the mapList.setOnKeyPressed()
         * thing below which has the same keys mapped
         */
        //TODO: Change accelerator text from an arrow to "Enter"
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.OPEN)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final File selectedMap = mapList.getSelectionModel().getSelectedItem();

                        for (final Tab tab : mainTabPane.getTabs()) {
                            if (tab.getClass().equals(MapEditTab.class) &&
                                tab.getId().equals(selectedMap.getName().replace(".pxpack", ""))) {
                                mainTabPane.getSelectionModel().select(tab);
                                return;
                            }
                        }

                        final MapEditTab mapEditTab = new MapEditTab(mapList.getSelectionModel().getSelectedItem());
                        mainTabPane.getTabs().add(mapEditTab);
                        mainTabPane.getSelectionModel().select(mapEditTab);
                    }
                });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.NEW)]
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.NEW)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                        //TODO: When I implement this, use Task
                    /*File f;
                    for (int i = 0; ; ++i) {
                        f = new File(GameData.getExecutable().getParent() + GameData.getResourceFolder());
                        if (!f.exists()) {
                            //create a PxPackMap object with f as parameter
                        }
                    }*/
                    }
                });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.DELETE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.DELETE)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final File map = mapList.getSelectionModel().getSelectedItem();
                        GameData.removeMap(map);
                        mapList.getItems().remove(map);

                        for (final Tab tab : mainTabPane.getTabs()) {
                            if (tab.getClass().equals(MapEditTab.class) && tab.getId().equals(map.getName().replace(".pxpack", ""))) {
                                mainTabPane.getTabs().remove(tab);
                                break;
                            }
                        }
                    }
                });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.RENAME)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });

        contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.DUPLICATE)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                        //TODO: Create prompt for new mapname
                    }
                });

        mapList.setOnKeyPressed(new EventHandler <KeyEvent>() {
            @Override
            public void handle(final KeyEvent event) {
                //Retrieves the index of the MenuItem in the context menu that the key is tied to
                if (event.getCode().equals(KeyCode.CONTEXT_MENU)) {
                    contextMenu.show(mainBorderPane, 0, 0);
                    return;
                }

                final int menuItemIndex = contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.OPEN)]
                                                  .getAccelerator().match(event) ?
                                          mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.OPEN) :

                                          contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.NEW)]
                                                  .getAccelerator().match(event) ?
                                          mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.NEW) :

                                          contextMenuActions[mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.DELETE)]
                                                  .getAccelerator().match(event) ?
                                          mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.DELETE) : -1;

                if (-1 != menuItemIndex) {
                    contextMenu.getItems().get(menuItemIndex).getOnAction().handle(new ActionEvent());
                }
            }
        });

        mapList.setOnMouseClicked(new EventHandler <MouseEvent>() {
            @Override
            public void handle(final MouseEvent event) {
                if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()) {
                    contextMenu.getItems().get(mapListContextMenuItemIndexes.get(MapListContextMenuItemIndexes.OPEN))
                               .getOnAction().handle(new ActionEvent());
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
    private void initTabPane() {
        mainTabPane = new TabPane();

        mainTabPane.setPrefHeight(mainStage.getHeight());
        mainTabPane.setPrefWidth(mainStage.getWidth());

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);
        JavaFXUtil.addCloseTabKeys(mainTabPane);

        mainBorderPane.setCenter(mainTabPane);
    }

    private enum MenuBarMenuIndexes {
        FILE,
        EDIT,
        VIEW,
        HELP
    }

    private enum FileMenuItemIndexes {
        OPEN,
        OPEN_LAST,
        SAVE,
        SAVE_ALL,
        RELOAD,
        CLOSE_TAB,
        CLOSE_ALL_TABS
    }

    private enum EditMenuItemIndexes {
        UNDO,
        REDO
    }

    private enum ViewMenuItemIndexes {
        MAP_ZOOM,
        TILESET_ZOOM,
        TILESET_BG_COLOR
    }

    private enum HelpMenuItemIndexes {
        ABOUT,
        GUIDE
    }

    private enum MapListContextMenuItemIndexes {
        OPEN,
        NEW,
        DELETE,
        DUPLICATE,
        RENAME
    }
}