/*
 * TODO:
 * Add save to MapEditTab
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
 * Allow configuring tile size?
 * Use iterators where possible
 * Investigate tab-related exceptions, slowness, and NoClassDefFoundError exception when trying to save prefs
 * Log runtime/uncaught exceptions
 * Scaling map down
 * Shorten tertiary statements
 * Lower memory usage and stuffs
 * Allow changing tilesets in map edit tab (so it will have to change head properties)
 * Find most efficient way to read files
 * Play pxtone files
 * Set layer on save or have PxPack give direct access?
 * Will need something for GameData changes to notify objects using it (i.e. maplist changes)
 * In script editor, eventually put in an autocompleter for stuff like entity names
 *
 * Create missing directories rather than throw error?
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

import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;

import javafx.geometry.Orientation;
import javafx.geometry.Insets;

import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.CheckBox;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ColorPicker;

import javafx.scene.control.ListView;
import javafx.stage.WindowEvent;
import javafx.collections.FXCollections;

import javafx.scene.control.Alert;

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;

import javafx.event.ActionEvent;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;

import javafx.scene.input.MouseButton;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

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

import com.fdl.keroedit.map.PxPack;

import com.fdl.keroedit.mapedit.MapEditTab;

public class KeroEdit extends Application {
    private ExecutorService prefsExecService;
    private Phaser prefsPhaser;

    private Stage mainStage;
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
        prefsExecService = Executors.newSingleThreadExecutor();
        prefsPhaser = new Phaser();

        loadPreferences();

        initStage(stage);
    }

    private void loadPreferences() {
        prefsPhaser.register();

        prefsExecService.execute(new Task <Void>() {
            @Override
            protected Void call() {
                prefsPhaser.arriveAndAwaitAdvance();
                Config.loadPreferences();
                prefsPhaser.arriveAndDeregister();
                return null;
            }
        });
    }

    private void savePreferences() {
        prefsPhaser.register();

        prefsExecService.execute(new Task <Void>() {
            @Override
            protected Void call() {
                prefsPhaser.arriveAndAwaitAdvance();
                Config.savePreferences();
                prefsPhaser.arriveAndDeregister();
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
    private void initStage(final Stage stage) {
        mainStage = stage;

        mainStage.setOnCloseRequest((final WindowEvent event) -> {
            savePreferences();

            prefsExecService.shutdown();
            mainStage.close();
            event.consume();
            //TODO: Warn about unsaved changes
        });

        //Note to self - keep these as BorderPanes - while a VBox may conceptually seem more fit for this
        //it does not resize well and there's a whole load of sizing issues
        final BorderPane rightSidePane = new BorderPane(initTabPane());
        rightSidePane.setTop(initSettingsPane());

        final SplitPane sPane = new SplitPane(initMapList(), rightSidePane);
        sPane.setDividerPositions(0.1);

        final BorderPane container = new BorderPane(sPane);
        container.setTop(initMenuBar());

        final Rectangle2D display = Screen.getPrimary().getVisualBounds();
        final Scene scene = new Scene(container, display.getWidth(), display.getHeight());

        mainStage.setScene(scene);
        mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.APP_TITLE"),
                                                Messages.getString("KeroEdit.VERSION")));

        mainStage.show();
        mainStage.requestFocus();
    }

    /**
     * Sets up the menu bar that appears at the top
     *
     * @return The created {@code MenuBar}
     */
    private MenuBar initMenuBar() {
        final MenuBar mBar = new javafx.scene.control.MenuBar();
        mBar.prefWidthProperty().bind(mainStage.widthProperty());
        mBar.setUseSystemMenuBar(true);

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
        final EnumMap <MenuBarItems, Integer> menuBarItems = new EnumMap <>(MenuBarItems.class);
        final EnumMap <FileMenuItems, Integer> fileMenuItems = new EnumMap <>(FileMenuItems.class);
        final EnumMap <EditMenuItems, Integer> editMenuItems = new EnumMap <>(EditMenuItems.class);
        final EnumMap <ViewMenuItems, Integer> viewMenuItems = new EnumMap <>(ViewMenuItems.class);
        final EnumMap <ActionsMenuItems, Integer> actionsMenuItems = new EnumMap <>(ActionsMenuItems.class);
        final EnumMap <HelpMenuItems, Integer> helpMenuItems = new EnumMap <>(HelpMenuItems.class);

        int i = 0;
        for (final MenuBarItems x : MenuBarItems.values()) {
            menuBarItems.put(x, i++);
        }
        i = 0;
        for (final FileMenuItems x : FileMenuItems.values()) {
            fileMenuItems.put(x, i++);
        }
        i = 0;
        for (final EditMenuItems x : EditMenuItems.values()) {
            editMenuItems.put(x, i++);
        }
        i = 0;
        for (final ViewMenuItems x : ViewMenuItems.values()) {
            viewMenuItems.put(x, i++);
        }
        i = 0;
        for (final ActionsMenuItems x : ActionsMenuItems.values()) {
            actionsMenuItems.put(x, i++);
        }
        i = 0;
        for (final HelpMenuItems x : HelpMenuItems.values()) {
            helpMenuItems.put(x, i++);
        }

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.OPEN)]
                .setOnAction((event) -> {
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
                });

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setOnAction((event) -> {
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
                    catch (final NoSuchFileException except) {
                        JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                               Messages.getString("KeroEdit.OpenFile.InvalidPath.TITLE"),
                                               null,
                                               Messages.getString("KeroEdit.OpenFile.InvalidPath.MESSAGE"))
                                  .showAndWait();
                    }
                });

        prefsPhaser.register();
        prefsPhaser.arriveAndAwaitAdvance();
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setDisable(Config.lastExeLoc.equals(System.getProperty("user.dir")) || !Config.lastExeLoc.endsWith(".exe")); //Disabled if there is no last mod
        prefsPhaser.arriveAndDeregister();

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.SAVE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.SAVE)]
                .setOnAction((event) -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                               Messages.getString("KeroEdit.FileMenu.SAVE").replace("_", ""),
                                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                  .showAndWait());
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.SAVE)]
                .setDisable(true);

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setOnAction((event) -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                               Messages.getString("KeroEdit.FileMenu.SAVE_ALL").replace("_", ""),
                                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                  .showAndWait());
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.RELOAD)]
                .setAccelerator(new KeyCodeCombination(KeyCode.F5));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.RELOAD)]
                .setOnAction((event) -> {
                    //TODO: Put into Task? (probably not)

                    //TODO: Check if mod was deleted or modified since last load
                    //TODO: Warn about unsaved changes
                    mainTabPane.getTabs().clear(); //close all open tabs
                    mapList.getItems().clear();

                    try {
                        GameData.initGameData(GameData.getExecutable());
                        loadMapList();
                    }
                    catch (final NoSuchFileException except) {
                        JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                               Messages.getString("KeroEdit.OpenFile.InvalidPath.TITLE"),
                                               null,
                                               Messages.getString("KeroEdit.OpenFile.InvalidPath.MESSAGE"))
                                  .showAndWait();
                    }
                });
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.RELOAD)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.CLOSE_TAB)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.CLOSE_TAB)]
                .setOnAction((event) -> {
                    final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
                    if (-1 != tabIndex) {
                        mainTabPane.getTabs().remove(tabIndex);
                    }
                });
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.CLOSE_TAB)]
                .setDisable(true);

        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setOnAction((event) -> mainTabPane.getTabs().clear());
        menuItems[menuBarItems.get(MenuBarItems.FILE)][fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setDisable(true);

        menuItems[menuBarItems.get(MenuBarItems.EDIT)][editMenuItems.get(EditMenuItems.UNDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.EDIT)][editMenuItems.get(EditMenuItems.UNDO)]
                .setOnAction((event) -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                               Messages.getString("KeroEdit.EditMenu.UNDO").replace("_", ""),
                                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                  .showAndWait());
        menuItems[menuBarItems.get(MenuBarItems.EDIT)][editMenuItems.get(EditMenuItems.UNDO)]
                .setDisable(true); //Disable until valid mod opened

        menuItems[menuBarItems.get(MenuBarItems.EDIT)][editMenuItems.get(EditMenuItems.REDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        menuItems[menuBarItems.get(MenuBarItems.EDIT)][editMenuItems.get(EditMenuItems.REDO)]
                .setOnAction((event) -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                               Messages.getString("KeroEdit.EditMenu.REDO").replace("_", ""),
                                                               null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                  .showAndWait());
        menuItems[menuBarItems.get(MenuBarItems.EDIT)][editMenuItems.get(EditMenuItems.REDO)]
                .setDisable(true); //Disable until valid mod opened

        final RadioMenuItem[] mapZoomMenuItems = createZoomMenu(Config.mapZoom);
        int zoom = 2;
        for (i = 0; i < mapZoomMenuItems.length; ++i) {
            final int z = zoom;
            mapZoomMenuItems[i].setOnAction((event) -> {
                Config.mapZoom = z;
                MapEditTab.setMapZoom(z);
            });
            zoom += 2;
        }
        ((Menu)menuItems[menuBarItems.get(MenuBarItems.VIEW)][viewMenuItems.get(ViewMenuItems.MAP_ZOOM)]).getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = createZoomMenu(Config.tilesetZoom);
        zoom = 2;
        for (i = 0; i < tilesetZoomMenuItems.length; ++i) {
            final int z = zoom;
            tilesetZoomMenuItems[i].setOnAction((event) -> {
                Config.tilesetZoom = z;
                MapEditTab.setTilesetZoom(z);
            });
            zoom += 2;
        }
        ((Menu)menuItems[menuBarItems.get(MenuBarItems.VIEW)][viewMenuItems.get(ViewMenuItems.TILESET_ZOOM)]).getItems().addAll(tilesetZoomMenuItems);

        menuItems[menuBarItems.get(MenuBarItems.VIEW)][viewMenuItems.get(ViewMenuItems.TILESET_BG_COLOR)]
                .setOnAction((event) -> {
                    final ColorPicker cPicker = new ColorPicker(Config.tilesetBgColor);
                    cPicker.setOnAction((ev) -> {
                        Config.tilesetBgColor = cPicker.getValue();
                        MapEditTab.setTilesetBgColor(cPicker.getValue());
                    });

                    final Dialog <Void> cPickerDialog = new Dialog <>();
                    cPickerDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    cPickerDialog.getDialogPane().setContent(cPicker);
                    cPickerDialog.showAndWait();
                });

        menuItems[menuBarItems.get(MenuBarItems.ACTIONS)][actionsMenuItems.get(ActionsMenuItems.RUN_GAME)]
                .setOnAction((event) -> {
                    final Runtime run = Runtime.getRuntime();
                    try {
                        run.exec(GameData.getExecutable().getAbsolutePath());
                    }
                    catch (final IOException except) {
                        JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                               Messages.getString("KeroEdit.RunGame.IOExcept.TITLE"), null,
                                               Messages.getString("KeroEdit.RunGame.IOExcept.MESSAGE"));
                    }
                });
        menuItems[menuBarItems.get(MenuBarItems.ACTIONS)][actionsMenuItems.get(ActionsMenuItems.RUN_GAME)]
                .setDisable(true);

        menuItems[menuBarItems.get(MenuBarItems.HELP)][helpMenuItems.get(HelpMenuItems.ABOUT)]
                .setOnAction((event) -> {
                    final Alert about = JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                               Messages.getString("KeroEdit.HelpMenu.About.TITLE"),
                                                               null,
                                                               MessageFormat.format(Messages.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                                    Messages.getString("KeroEdit.LAST_UPDATE"),
                                                                                    Messages.getString("KeroEdit.VERSION")));

                    about.getDialogPane().setGraphic(new ImageView(ResourceManager.getImage("fdl_logo.png")));
                    about.showAndWait();
                });

        menuItems[menuBarItems.get(MenuBarItems.HELP)][helpMenuItems.get(HelpMenuItems.GUIDE)]
                .setOnAction((event) ->
                                     JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                            Messages.getString("KeroEdit.HelpMenu.GUIDE").replace("_", ""),
                                                            null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                               .showAndWait());

        return mBar;
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

    private GridPane initSettingsPane() {
        final GridPane settingsPane = new GridPane();
        settingsPane.setPadding(new Insets(10, 10, 10, 10));
        settingsPane.setVgap(10);
        settingsPane.setHgap(20);

        int x = 0, y = 0;

        final Text displayedLayersLabel = new Text(Messages.getString("KeroEdit.DISPLAYED_LAYERS"));
        displayedLayersLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
        settingsPane.add(displayedLayersLabel, x, y);

        final CheckBox[] displayedLayersCheckboxes = new CheckBox[PxPack.NUM_LAYERS];
        for (int i = 0; i < displayedLayersCheckboxes.length; ++i) {
            displayedLayersCheckboxes[i] = new CheckBox(Messages.getString(0 == i ?
                                                                           "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                  : 1 == i ?
                                                                                    "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                           : "MapEditTab.TileEditTab.Layers.BACKGROUND"));
            displayedLayersCheckboxes[i].setAllowIndeterminate(false);
            displayedLayersCheckboxes[i].setSelected(true);
            MapEditTab.bindDisplayedLayer(i, displayedLayersCheckboxes[i].selectedProperty());

            settingsPane.add(displayedLayersCheckboxes[i], x, y + i + 1);
        }

        final Text selectedLayerLabel = new Text(Messages.getString("KeroEdit.SELECTED_LAYER"));
        selectedLayerLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
        settingsPane.add(selectedLayerLabel, ++x, y);

        final ToggleGroup selectedLayerToggleGroup = new ToggleGroup();
        final RadioButton[] selectedLayerRadioButtons = new RadioButton[PxPack.NUM_LAYERS];
        for (int i = 0; i < selectedLayerRadioButtons.length; ++i) {
            selectedLayerRadioButtons[i] = new RadioButton(Messages.getString(0 == i ?
                                                                              "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                     : 1 == i ?
                                                                                       "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                              : "MapEditTab.TileEditTab.Layers.BACKGROUND"));
            selectedLayerRadioButtons[i].setToggleGroup(selectedLayerToggleGroup);

            if (0 == i) {
                selectedLayerRadioButtons[i].setSelected(true);
            }

            final int layer = i;
            selectedLayerRadioButtons[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    MapEditTab.setSelectedLayer(layer);
                }
            });
            settingsPane.add(selectedLayerRadioButtons[i], x, y + i + 1);
        }

        final Text viewSettingsLabel = new Text(Messages.getString("MapEditTab.TileEditTab.VIEW_SETTINGS"));
        viewSettingsLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
        settingsPane.add(viewSettingsLabel, ++x, 0);

        final CheckBox toggle = new CheckBox(Messages.getString("MapEditTab.TileEditTab.SHOW_TILE_TYPES_TEXT"));
        toggle.setSelected(false);
        MapEditTab.bindShowTileTypes(toggle.selectedProperty());

        settingsPane.add(toggle, x, y + 1);

        return settingsPane;
    }

    /**
     * Creates the map list {@code ListView},
     * but does not put any maps into it, and binds no actions to it.
     *
     * @return The created {@code ListView}
     */
    private ListView initMapList() {
        mapList = new ListView <>();
        mapList.setOrientation(Orientation.VERTICAL);

        mapList.setMinWidth(125);
        mapList.setPrefWidth(125);

        return mapList;
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

        final EnumMap <MapListMenuItems, Integer> mapListMenuItems = new EnumMap <>(MapListMenuItems.class);
        int i = 0;
        for (final MapListMenuItems x : MapListMenuItems.values()) {
            mapListMenuItems.put(x, i++);
        }

        /*
         * Accelerators present more so to show key mappings, as the accelerators are not triggered
         * (probably since they're in a context menu). So I have the mapList.setOnKeyPressed()
         * thing below which has the same keys mapped
         */
        //TODO: Change accelerator text from an arrow to "Enter"
        contextMenuActions[mapListMenuItems.get(MapListMenuItems.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuActions[mapListMenuItems.get(MapListMenuItems.OPEN)]
                .setOnAction((event) -> {
                    final String filename = mapList.getSelectionModel().getSelectedItem();
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab.getClass().equals(MapEditTab.class) &&
                            tab.getId().equals(filename)) {

                            mainTabPane.getSelectionModel().select(tab);
                            mainTabPane.requestFocus();
                            return;
                        }
                    }

                    final MapEditTab mapEditTab = new MapEditTab(mapList.getSelectionModel().getSelectedItem());
                    mainTabPane.getTabs().add(mapEditTab);
                    mainTabPane.getSelectionModel().select(mapEditTab);
                    mainTabPane.requestFocus();
                });

        contextMenuActions[mapListMenuItems.get(MapListMenuItems.NEW)]
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        contextMenuActions[mapListMenuItems.get(MapListMenuItems.NEW)]
                .setOnAction((event) -> {
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
                });

        contextMenuActions[mapListMenuItems.get(MapListMenuItems.DELETE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuActions[mapListMenuItems.get(MapListMenuItems.DELETE)]
                .setOnAction((event) -> {
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
                });

        contextMenuActions[mapListMenuItems.get(MapListMenuItems.RENAME)]
                .setOnAction((event) ->
                                     JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                            Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP").replace("_", ""),
                                                            null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                               .showAndWait());

        contextMenuActions[mapListMenuItems.get(MapListMenuItems.DUPLICATE)]
                .setOnAction((event) ->
                                     //TODO: Create prompt for new mapname
                                     JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                            Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP").replace("_", ""),
                                                            null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                               .showAndWait());

        mapList.setOnKeyPressed((event) -> {
            //Retrieves the index of the MenuItem in the context menu that the key is tied to
            final int menuItemIndex = contextMenuActions[mapListMenuItems.get(MapListMenuItems.OPEN)]
                                              .getAccelerator().match(event) ?
                                      mapListMenuItems.get(MapListMenuItems.OPEN) :

                                      contextMenuActions[mapListMenuItems.get(MapListMenuItems.NEW)]
                                              .getAccelerator().match(event) ?
                                      mapListMenuItems.get(MapListMenuItems.NEW) :

                                      contextMenuActions[mapListMenuItems.get(MapListMenuItems.DELETE)]
                                              .getAccelerator().match(event) ?
                                      mapListMenuItems.get(MapListMenuItems.DELETE) : -1;

            if (-1 != menuItemIndex) {
                contextMenu.getItems().get(menuItemIndex).getOnAction().handle(new ActionEvent());
            }
        });

        mapList.setOnMouseClicked((event) -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()) {
                contextMenu.getItems().get(mapListMenuItems.get(MapListMenuItems.OPEN))
                           .getOnAction().handle(new ActionEvent());
            }
        });
    }

    /**
     * Sets up the {@code TabPane} that forms the core of the KeroEdit program
     *
     * @return The created {@code TabPane}
     */
    private TabPane initTabPane() {
        mainTabPane = new TabPane();

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);

        return mainTabPane;
    }

    private enum MenuBarItems {
        FILE,
        EDIT,
        VIEW,
        ACTIONS,
        HELP
    }

    private enum FileMenuItems {
        OPEN,
        OPEN_LAST,
        SAVE,
        SAVE_ALL,
        RELOAD,
        CLOSE_TAB,
        CLOSE_ALL_TABS
    }

    private enum EditMenuItems {
        UNDO,
        REDO
    }

    private enum ViewMenuItems {
        MAP_ZOOM,
        TILESET_ZOOM,
        TILESET_BG_COLOR
    }

    private enum ActionsMenuItems {
        RUN_GAME
    }

    private enum HelpMenuItems {
        ABOUT,
        GUIDE
    }

    private enum MapListMenuItems {
        OPEN,
        NEW,
        DELETE,
        DUPLICATE,
        RENAME
    }
}