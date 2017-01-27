/*
 * TODO:
 * Add save to MapEditTab
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
 * Allow changing background color in map editing tab (so it will have to change head properties)
 * Allow changing tilesets in map edit tab (so it will have to change head properties)
 * Find most efficient way to read files
 * Play pxtone files
 * Set layer on save or have PxPack give direct access?
 * Switch back to three canvas & stackpane system? (slow...could try tasks)
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

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import java.util.EnumMap;

import java.text.MessageFormat;

import javafx.application.Application;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ColorPicker;

import javafx.scene.control.ListView;
import javafx.stage.WindowEvent;
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
    private static ExecutorService mainLogicExecutorService;
    private Phaser mainLogicExecutorPhaser;

    private Stage mainStage;
    private BorderPane mainBorderPane;
    private TabPane mainTabPane;

    private ListView <String> mapList;

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

    public static ExecutorService getExecService() {
        return mainLogicExecutorService;
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
        mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.APP_TITLE"),
                                                Messages.getString("KeroEdit.VERSION")));
        mainStage.show();
        mainStage.requestFocus();
    }

    /**
     * Sets up the menu bar that appears at the top
     */
    private void initMenuBar() {
        final javafx.scene.control.MenuBar mBar = new javafx.scene.control.MenuBar();
        mBar.setPrefWidth(mainStage.getWidth());

        /*
         * Underscores underline the following letter, allowing Alt + letter to be used as a 'hotkey'
         * or mnemonic (at least on Windows)
         */
        final Menu[] menus = {new Menu(Messages.getString("KeroEdit.FILE_MENU")),
                              new Menu(Messages.getString("KeroEdit.EDIT_MENU")),
                              new Menu(Messages.getString("KeroEdit.VIEW_MENU")),
                              new Menu(Messages.getString("KeroEdit.ACTIONS_MENU")),
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

                                        {new MenuItem(Messages.getString("KeroEdit.ActionsMenu.RUN_GAME"))},

                                        {new MenuItem(Messages.getString("KeroEdit.HelpMenu.ABOUT")),
                                         new MenuItem(Messages.getString("KeroEdit.HelpMenu.GUIDE"))}};

        for (int i = 0; i < menus.length; ++i) {
            menus[i].getItems().addAll(menuItems[i]);
        }

        //TODO: Put into task
        final EnumMap <MenuBar, Integer> menuBar = new EnumMap <MenuBar, Integer>(MenuBar.class);
        final EnumMap <FileMenu, Integer> fileMenu = new EnumMap <FileMenu, Integer>(FileMenu.class);
        final EnumMap <EditMenu, Integer> editMenu = new EnumMap <EditMenu, Integer>(EditMenu.class);
        final EnumMap <ViewMenu, Integer> viewMenu = new EnumMap <ViewMenu, Integer>(ViewMenu.class);
        final EnumMap <ActionsMenu, Integer> actionsMenu = new EnumMap <ActionsMenu, Integer>(ActionsMenu.class);
        final EnumMap <HelpMenu, Integer> helpMenu = new EnumMap <HelpMenu, Integer>(HelpMenu.class);

        int i = 0;
        for (final MenuBar x : MenuBar.values()) {
            menuBar.put(x, i++);
        }
        i = 0;
        for (final FileMenu x : FileMenu.values()) {
            fileMenu.put(x, i++);
        }
        i = 0;
        for (final EditMenu x : EditMenu.values()) {
            editMenu.put(x, i++);
        }
        i = 0;
        for (final ViewMenu x : ViewMenu.values()) {
            viewMenu.put(x, i++);
        }
        i = 0;
        for (final ActionsMenu x : ActionsMenu.values()) {
            actionsMenu.put(x, i++);
        }
        i = 0;
        for (final HelpMenu x : HelpMenu.values()) {
            helpMenu.put(x, i++);
        }

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.OPEN)]
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
                                                                        Messages.getString("KeroEdit.VERSION"),
                                                                        executable.getParent() + File.separatorChar));

                                loadMapList();
                                //TODO: Check if actions already bound?
                                bindMapListActions();

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

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.OPEN_LAST)]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.OPEN_LAST)]
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
                                                                    Messages.getString("KeroEdit.VERSION"),
                                                                    lastExeLoc.substring(0, lastExeLoc.lastIndexOf(File.separatorChar) + 1)));

                            loadMapList();
                            bindMapListActions();

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
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.OPEN_LAST)]
                .setDisable(Config.lastExeLoc.equals(System.getProperty("user.dir")) || !Config.lastExeLoc.endsWith(".exe")); //Disabled if there is no last mod
        mainLogicExecutorPhaser.arriveAndDeregister();

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.SAVE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.SAVE)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.FileMenu.SAVE").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.SAVE)]
                .setDisable(true);

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.SAVE_ALL)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.SAVE_ALL)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.FileMenu.SAVE_ALL").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.SAVE_ALL)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.RELOAD)]
                .setAccelerator(new KeyCodeCombination(KeyCode.F5));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.RELOAD)]
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
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.RELOAD)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.CLOSE_TAB)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.CLOSE_TAB)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
                        if (-1 != tabIndex) {
                            mainTabPane.getTabs().remove(tabIndex);
                        }
                    }
                });
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.CLOSE_TAB)]
                .setDisable(true);

        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.CLOSE_ALL_TABS)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.CLOSE_ALL_TABS)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        mainTabPane.getTabs().clear();
                    }
                });
        menuItems[menuBar.get(MenuBar.FILE)][fileMenu.get(FileMenu.CLOSE_ALL_TABS)]
                .setDisable(true);

        menuItems[menuBar.get(MenuBar.EDIT)][editMenu.get(EditMenu.UNDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.EDIT)][editMenu.get(EditMenu.UNDO)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.EditMenu.UNDO").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBar.get(MenuBar.EDIT)][editMenu.get(EditMenu.UNDO)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBar.get(MenuBar.EDIT)][editMenu.get(EditMenu.REDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        menuItems[menuBar.get(MenuBar.EDIT)][editMenu.get(EditMenu.REDO)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.EditMenu.REDO").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBar.get(MenuBar.EDIT)][editMenu.get(EditMenu.REDO)]
                .setDisable(true); //Disable until valid mod opened

        final RadioMenuItem[] mapZoomMenuItems = createZoomMenu(Config.mapZoom);
        int zoom = 2;
        for (i = 0; i < mapZoomMenuItems.length; ++i) {
            final int z = zoom;
            mapZoomMenuItems[i].setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Config.mapZoom = z;
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab.getClass().equals(MapEditTab.class)) {
                            final MapEditTab mEditTab = (MapEditTab)tab;
                            mEditTab.setMapZoom(z);
                        }
                    }
                }
            });
            zoom += 2;
        }
        final Menu mapZoomSubMenu = (Menu)menuItems[menuBar.get(MenuBar.VIEW)]
                [viewMenu.get(ViewMenu.MAP_ZOOM)];
        mapZoomSubMenu.getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = createZoomMenu(Config.tilesetZoom);
        zoom = 2;
        for (i = 0; i < tilesetZoomMenuItems.length; ++i) {
            final int z = zoom;
            tilesetZoomMenuItems[i].setOnAction(new EventHandler <ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    Config.tilesetZoom = z;
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab.getClass().equals(MapEditTab.class)) {
                            final MapEditTab mEditTab = (MapEditTab)tab;
                            mEditTab.setTilesetZoom(z);
                        }
                    }
                }
            });
            zoom += 2;
        }
        final Menu tilesetZoomSubMenu = (Menu)menuItems[menuBar.get(MenuBar.VIEW)]
                [viewMenu.get(ViewMenu.TILESET_ZOOM)];
        tilesetZoomSubMenu.getItems().addAll(tilesetZoomMenuItems);

        menuItems[menuBar.get(MenuBar.VIEW)][viewMenu.get(ViewMenu.TILESET_BG_COLOR)]
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

        menuItems[menuBar.get(MenuBar.ACTIONS)][actionsMenu.get(ActionsMenu.RUN_GAME)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final Runtime run = Runtime.getRuntime();
                        try {
                            run.exec(GameData.getExecutable().getAbsolutePath());
                        }
                        catch (final IOException except) {
                            JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("KeroEdit.RunGame.IOExcept.TITLE"), null,
                                                   Messages.getString("KeroEdit.RunGame.IOExcept.MESSAGE"));
                        }
                    }
                });
        menuItems[menuBar.get(MenuBar.ACTIONS)][actionsMenu.get(ActionsMenu.RUN_GAME)]
                .setDisable(true);

        menuItems[menuBar.get(MenuBar.HELP)][helpMenu.get(HelpMenu.ABOUT)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final Alert about = JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                                   Messages.getString("KeroEdit.HelpMenu.About.TITLE"),
                                                                   null,
                                                                   MessageFormat.format(Messages.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                                        Messages.getString("KeroEdit.LAST_UPDATE"),
                                                                                        Messages.getString("KeroEdit.VERSION")));

                        about.getDialogPane().setGraphic(new ImageView(ResourceManager.getImage("fdl_logo.png")));
                        about.showAndWait();
                    }
                });

        menuItems[menuBar.get(MenuBar.HELP)][helpMenu.get(HelpMenu.GUIDE)]
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

    /**
     * Used for creating the Map and Tileset zoom submenus
     *
     * @param currentZoom The current/default zoom level
     *
     * @return An array of {@code RadioMenuItem}s in a {@code ToggleGroup} that can be used
     * to set a zoom level. None of them are bound to an {@code EventHandler <ActionEvent>},
     * so that must be done after they are created
     */
    private RadioMenuItem[] createZoomMenu(final int currentZoom) {
        final ToggleGroup zoomToggleGroup = new ToggleGroup();
        final RadioMenuItem[] zoomMenuItems = new RadioMenuItem[3];

        //from 200, 400, 600% since attribute.png and unittype.png are are 16px by 16px but tiles are 8px by 8px
        //and I don't have a half-scale image scaler yet
        int zoom = 2;
        for (int i = 0; i < zoomMenuItems.length; ++i) {
            zoomMenuItems[i] = new RadioMenuItem(String.valueOf(zoom * 100) + '%');

            zoomMenuItems[i].setToggleGroup(zoomToggleGroup);
            if (currentZoom == zoom) {
                zoomMenuItems[i].setSelected(true);
            }
            zoom += 2;
        }

        return zoomMenuItems;
    }

    /**
     * Creates the map list {@code List View} on the left,
     * but does not put any maps into it, and binds no actions to it.
     */
    private void initMapList() {
        mapList = new ListView <String>();

        mapList.setPrefHeight(mainStage.getHeight());

        mapList.setMinWidth(125); //TODO: Variable size these
        mapList.setPrefWidth(125);
        mapList.setMaxWidth(250);

        mapList.setOrientation(Orientation.VERTICAL);

        mapList.setOnMouseDragged(new EventHandler <MouseEvent>() { //TODO: Make this work less erratically
            double prevX;

            @Override
            public void handle(final MouseEvent event) {
                mapList.setPrefWidth(mapList.getPrefWidth() + event.getX() - prevX);
                prevX = event.getX();
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
    private void bindMapListActions() {
        final MenuItem[] contextMenuActions = {new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.OPEN_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DELETE_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")),
                                               new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP"))};
        //TODO: Add save map to context menu?

        final ContextMenu contextMenu = new ContextMenu(contextMenuActions);
        mapList.setContextMenu(contextMenu);

        final EnumMap <MapListContextMenu, Integer> mapListContextMenu
                = new EnumMap <MapListContextMenu, Integer>(MapListContextMenu.class);
        int i = 0;
        for (final MapListContextMenu x : MapListContextMenu.values()) {
            mapListContextMenu.put(x, i++);
        }

        /*
         * Accelerators present more so to show key mappings, as the accelerators are not triggered
         * (probably since they're in a context menu). So I have the mapList.setOnKeyPressed()
         * thing below which has the same keys mapped
         */
        //TODO: Change accelerator text from an arrow to "Enter"
        contextMenuActions[mapListContextMenu.get(MapListContextMenu.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuActions[mapListContextMenu.get(MapListContextMenu.OPEN)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final String filename = mapList.getSelectionModel().getSelectedItem();

                        for (final Tab tab : mainTabPane.getTabs()) {
                            if (tab.getClass().equals(MapEditTab.class) &&
                                tab.getId().equals(filename)) {

                                mainTabPane.getSelectionModel().select(tab);
                                return;
                            }
                        }

                        final MapEditTab mapEditTab = new MapEditTab(mapList.getSelectionModel().getSelectedItem());
                        mainTabPane.getTabs().add(mapEditTab);
                        mainTabPane.getSelectionModel().select(mapEditTab);
                    }
                });

        contextMenuActions[mapListContextMenu.get(MapListContextMenu.NEW)]
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        contextMenuActions[mapListContextMenu.get(MapListContextMenu.NEW)]
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

        contextMenuActions[mapListContextMenu.get(MapListContextMenu.DELETE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuActions[mapListContextMenu.get(MapListContextMenu.DELETE)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final String filename = mapList.getSelectionModel().getSelectedItem();
                        GameData.removeMap(filename);
                        mapList.getItems().remove(filename);

                        for (final Tab tab : mainTabPane.getTabs()) {
                            if (tab.getClass().equals(MapEditTab.class) &&
                                tab.getId().equals(filename)) {

                                mainTabPane.getTabs().remove(tab);
                                break;
                            }
                        }
                    }
                });

        contextMenuActions[mapListContextMenu.get(MapListContextMenu.RENAME)]
                .setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                               Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP").replace("_", ""),
                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                  .showAndWait();
                    }
                });

        contextMenuActions[mapListContextMenu.get(MapListContextMenu.DUPLICATE)]
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
                final int menuItemIndex = contextMenuActions[mapListContextMenu.get(MapListContextMenu.OPEN)]
                                                  .getAccelerator().match(event) ?
                                          mapListContextMenu.get(MapListContextMenu.OPEN) :

                                          contextMenuActions[mapListContextMenu.get(MapListContextMenu.NEW)]
                                                  .getAccelerator().match(event) ?
                                          mapListContextMenu.get(MapListContextMenu.NEW) :

                                          contextMenuActions[mapListContextMenu.get(MapListContextMenu.DELETE)]
                                                  .getAccelerator().match(event) ?
                                          mapListContextMenu.get(MapListContextMenu.DELETE) : -1;

                if (-1 != menuItemIndex) {
                    contextMenu.getItems().get(menuItemIndex).getOnAction().handle(new ActionEvent());
                }
            }
        });

        mapList.setOnMouseClicked(new EventHandler <MouseEvent>() {
            @Override
            public void handle(final MouseEvent event) {
                if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()) {
                    contextMenu.getItems().get(mapListContextMenu.get(MapListContextMenu.OPEN))
                               .getOnAction().handle(new ActionEvent());
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

        mainBorderPane.setCenter(mainTabPane);
    }

    private enum MenuBar {
        FILE,
        EDIT,
        VIEW,
        ACTIONS,
        HELP
    }

    private enum FileMenu {
        OPEN,
        OPEN_LAST,
        SAVE,
        SAVE_ALL,
        RELOAD,
        CLOSE_TAB,
        CLOSE_ALL_TABS
    }

    private enum EditMenu {
        UNDO,
        REDO
    }

    private enum ViewMenu {
        MAP_ZOOM,
        TILESET_ZOOM,
        TILESET_BG_COLOR
    }

    private enum ActionsMenu {
        RUN_GAME
    }

    private enum HelpMenu {
        ABOUT,
        GUIDE
    }

    private enum MapListContextMenu {
        OPEN,
        NEW,
        DELETE,
        DUPLICATE,
        RENAME
    }
}