/*
 * TODO:
 * Barebones png editor (color picker and canvas) and reload tilesets in open maps on save (or on edit?)
 * Ctrl +/- and scrollwheel for zoom
 * figure out url bug with assist folder (file:/)
 * Use switch statements where applicable
 * Undoable map delete?
 * Put enum filler into method in util package
 * Use Tab.setOnCloseRequest() to warn about unsaved changes
 * Resort the map ListView alphabetically when a map is added, and select and open the new map
 * Draggable tabs (and allow popping out into a window)
 * Pass object constructors Files still or move to strings relative to GameData?
 * Use nio.Files and nio.Path instead of io.File?
 * Allow opening multiple maps at once (multiple selection)
 * Allow configuring tile size?
 * Use iterators where possible
 * Investigate tab-related exceptions, slowness, and NoClassDefFoundError exception when trying to save prefs
 * Log runtime/uncaught exceptions
 * Scaling map down
 * Lower memory usage and stuffs
 * Allow changing tilesets in map edit tab (so it will have to change head properties)
 * Find most efficient way to read files
 * Play pxtone files
 * Will need something for GameData changes to notify objects using it (i.e. maplist changes)
 * In script editor, eventually put in an autocompleter for stuff like entity names
 *
 * Create missing directories rather than throw error?
 * Find OS-dependent stylesheets?
 */

package io.fdeitylink.keroedit;

import java.io.File;
import java.io.FileReader;

import java.nio.file.Files;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import java.util.ArrayList;
import java.util.EnumMap;

import java.text.MessageFormat;

import io.fdeitylink.keroedit.hack.HackTab;
import io.fdeitylink.keroedit.map.PxPack;
import io.fdeitylink.keroedit.mapedit.MapEditTab;
import io.fdeitylink.keroedit.resource.ResourceManager;
import io.fdeitylink.keroedit.script.ScriptEditTab;
import io.fdeitylink.keroedit.util.FileEditTab;
import io.fdeitylink.keroedit.util.JavaFXUtil;
import javafx.application.Application;
import javafx.application.Platform;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
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

import javafx.scene.control.TextArea;

import javafx.stage.FileChooser;

import javafx.print.PrinterJob;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import io.fdeitylink.keroedit.gamedata.GameData;

public class KeroEdit extends Application {
    private ArrayList <MenuItem> enableOnLoadItems;

    private Stage mainStage;
    private TabPane mainTabPane;

    private Tab notepadTab;

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
        Config.loadPreferences();
        initStage(stage);
    }

    /**
     * Appends a given string to the base title of the program's {@code Stage}
     *
     * @param str The string to append to the title
     */
    public void setTitle(final String str) {
        mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.APP_TITLE"),
                                                Messages.getString("KeroEdit.VERSION")) + " " + str);
    }

    /**
     * Initializes the {@code Stage}, including its
     * components by calling the other init methods
     *
     * @param stage The stage to run the Kero Edit program in
     */
    private void initStage(final Stage stage) {
        mainStage = stage;

        mainStage.setOnCloseRequest(event -> {
            Config.notepadText = ((TextArea)notepadTab.getContent()).getText();
            Config.savePreferences();

            mainStage.close();

            Platform.exit(); //graceful shutdown & closes all child windows

            event.consume();
            //TODO: Warn about unsaved changes
        });

        enableOnLoadItems = new ArrayList <>();

        /*
         * Note to self - keep these as BorderPanes - while a VBox may conceptually seem more fit for this
         * it does not resize well and there's a whole load of sizing issues
         */
        final BorderPane right = new BorderPane(initTabPane());
        right.setTop(new SettingsPane());

        final SplitPane sPane = new SplitPane(initMapList(), right);
        sPane.setDividerPositions(0.1);

        final BorderPane root = new BorderPane(sPane);
        root.setTop(initMenuBar());

        final Rectangle2D displayRect = Screen.getPrimary().getVisualBounds();
        mainStage.setScene(new Scene(root, displayRect.getWidth(), displayRect.getHeight()));
        setTitle("");

        mainStage.show();
        mainStage.setMaximized(true);
        mainStage.requestFocus();

        if (!Config.licenseRead) {
            showLicense();
            if (!Config.licenseRead) {
                mainStage.close();
                Platform.exit();
                System.exit(0);
            }
        }
    }

    /**
     * Sets up the {@code MenuBar} that appears at the top
     *
     * @return The created {@code MenuBar}
     */
    private MenuBar initMenuBar() {
        final MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);
        menuBar.prefWidthProperty().bind(mainStage.widthProperty());

        menuBar.getMenus().addAll(initFileMenu(), initEditMenu(), initViewMenu(), initActionsMenu(), initHelpMenu());
        return menuBar;
    }

    /**
     * Initializes the File {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initFileMenu() {
        final Menu fileMenu = new Menu(Messages.getString("KeroEdit.FILE_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.getString("KeroEdit.FileMenu.OPEN")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.OPEN_LAST")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.SAVE")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.SAVE_ALL")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.RELOAD")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_TAB")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_ALL_TABS"))};
        fileMenu.getItems().addAll(menuItems);

        final EnumMap <FileMenuItems, Integer> fileMenuItems = new EnumMap <>(FileMenuItems.class);
        int i = 0;
        for (final FileMenuItems x : FileMenuItems.values()) {
            fileMenuItems.put(x, i++);
        }

        menuItems[fileMenuItems.get(FileMenuItems.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.OPEN)].setOnAction(event -> {
            mainTabPane.getTabs().retainAll(notepadTab);
            mapList.getItems().clear();

            /*
             * If the file chooser menu is closed after a mod had already been open, these should be
             * disabled and the title should be reset since no mod will be opened
             * (i.e. open a mod, then do Ctrl + O and then ESC and no mod will be open)
             */
            for (final MenuItem mItem : enableOnLoadItems) {
                if (!mItem.isDisable()) {
                    mItem.setDisable(true);
                }
            }
            setTitle("");

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
            loadMod(executable);
            setTitle("- " + executable.getParent() + File.separatorChar);

            for (final MenuItem mItem : enableOnLoadItems) {
                mItem.setDisable(false);
            }
        });

        menuItems[fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.OPEN_LAST)].setOnAction(event -> {
            //TODO: Put into Task
            mainTabPane.getTabs().retainAll(notepadTab);
            mapList.getItems().clear();

            loadMod(new File(Config.lastExeLoc));

            setTitle("- " + Config.lastExeLoc.substring(0, Config.lastExeLoc.lastIndexOf(File.separatorChar) + 1));

            loadMapList();
            for (final MenuItem mItem : enableOnLoadItems) {
                mItem.setDisable(false);
            }
        });

        /*prefsPhaser.register();
        prefsPhaser.arriveAndAwaitAdvance();*/
        menuItems[fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setDisable(Config.lastExeLoc.equals(System.getProperty("user.dir")) ||
                            !Config.lastExeLoc.endsWith(".exe")); //Disabled if there is no last mod
        //prefsPhaser.arriveAndDeregister();

        menuItems[fileMenuItems.get(FileMenuItems.SAVE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.SAVE)]
                .setOnAction(event -> {
                    final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
                    if (selectedTab instanceof FileEditTab) {
                        ((FileEditTab)selectedTab).save();
                    }
                });
        menuItems[fileMenuItems.get(FileMenuItems.SAVE)]
                .setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.SAVE)]);

        menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setOnAction(event -> {
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab instanceof FileEditTab) {
                            ((FileEditTab)tab).save();
                        }
                    }
                });
        menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)]);

        menuItems[fileMenuItems.get(FileMenuItems.RELOAD)]
                .setAccelerator(new KeyCodeCombination(KeyCode.F5));
        menuItems[fileMenuItems.get(FileMenuItems.RELOAD)].setOnAction(event -> {
            //TODO: Warn about unsaved changes
            mainTabPane.getTabs().retainAll(notepadTab);
            mapList.getItems().clear();

            loadMod(GameData.getExecutable());
        });
        menuItems[fileMenuItems.get(FileMenuItems.RELOAD)]
                .setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.RELOAD)]);

        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)].setOnAction(event -> {
            final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            if (-1 != tabIndex && mainTabPane.getTabs().get(tabIndex) != notepadTab) {
                mainTabPane.getTabs().remove(tabIndex);
            }
        });
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)]
                .setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)]);

        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)].setOnAction(event -> mainTabPane.getTabs().retainAll(notepadTab));
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]);

        return fileMenu;
    }

    /**
     * Loads a mod, checking if it is valid. Also creates its assist folder
     *
     * @param executable A {@code File} that references the executable for a mod
     */
    private void loadMod(final File executable) {
        if (null != executable) {
            try {
                //TODO: check extension?
                GameData.init(executable);
                Config.lastExeLoc = executable.getAbsolutePath();

                if (!executable.getParentFile().canWrite() && !executable.getParentFile().setWritable(true)) {
                    JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                           Messages.getString("KeroEdit.LoadMod.ReadOnly.TITLE"), null,
                                           Messages.getString("KeroEdit.LoadMod.ReadOnly.MESSAGE"));
                }

                //createAssistFolder();

                loadMapList();
            }
            catch (final NoSuchFileException except) {
                JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("KeroEdit.LoadMod.InvalidPath.TITLE"),
                                       null,
                                       Messages.getString("KeroEdit.LoadMod.InvalidPath.MESSAGE")).showAndWait();
            }
        }
    }

    /**
     * Creates the assist folder for a mod inside of its resource folder
     */
    private void createAssistFolder() {
        final File assistDir = new File(GameData.getResourceFolder().getAbsolutePath() +
                                        File.separatorChar + "assist");
        try {
            if (!assistDir.exists()) {
                Files.createDirectory(assistDir.toPath());
            }

            final String stringsFilename;
            switch (GameData.getModType()) {
                case KERO_BLASTER:
                    stringsFilename = "kero_strings.json";
                    break;
                case PINK_HOUR:
                    stringsFilename = "hour_strings.json";
                    break;
                case PINK_HEAVEN:
                default:
                    stringsFilename = "heaven_strings.json";
                    break;
            }

            final File[] assistFiles = ResourceManager.getFile("assist").listFiles();

            if (null != assistFiles) {
                for (final File assistFile : assistFiles) {
                    //skip if attribute png or the wrong *_strings.json file
                    if (assistFile.getName().equals("attribute.png") ||
                        (assistFile.getName().endsWith("_strings.json") && !assistFile.getName().equals(stringsFilename))) {
                        continue;
                    }

                    try {
                        final File destFile = new File(GameData.getResourceFolder().getAbsolutePath() +
                                                       File.separatorChar + "assist" +
                                                       File.separatorChar + assistFile.getName());
                        if (!destFile.exists()) {
                            Files.copy(assistFile.toPath(), destFile.toPath());
                        }
                    }
                    catch (final IOException except) {
                        JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                               Messages.getString("KeroEdit.CreateAssistFolder.CopyFileFail.TITLE"),
                                               null,
                                               MessageFormat.format(Messages.getString("KeroEdit.CreateAssistFolder.CopyFileFail.MESSAGE"),
                                                                    assistFile.getName()))
                                  .showAndWait();
                    }
                }
            }
        }
        catch (final IOException except) {
            JavaFXUtil.createAlert(Alert.AlertType.ERROR, null,
                                   Messages.getString("KeroEdit.CreateAssistFolder.CopyFolderFail.TITLE"),
                                   Messages.getString("KeroEdit.CreateAssistFolder.CopyFolderFail.MESSAGE")).showAndWait();
        }
    }

    /**
     * Initializes the Edit {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initEditMenu() {
        final Menu editMenu = new Menu(Messages.getString("KeroEdit.EDIT_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.getString("KeroEdit.EditMenu.UNDO")),
                                      new MenuItem(Messages.getString("KeroEdit.EditMenu.REDO"))};
        editMenu.getItems().addAll(menuItems);

        final EnumMap <EditMenuItems, Integer> editMenuItems = new EnumMap <>(EditMenuItems.class);
        int i = 0;
        for (final EditMenuItems x : EditMenuItems.values()) {
            editMenuItems.put(x, i++);
        }

        menuItems[editMenuItems.get(EditMenuItems.UNDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        menuItems[editMenuItems.get(EditMenuItems.UNDO)]
                .setOnAction(event -> {
                    final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
                    if (selectedTab instanceof FileEditTab) {
                        ((FileEditTab)selectedTab).undo();
                    }
                });
        menuItems[editMenuItems.get(EditMenuItems.UNDO)]
                .setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[editMenuItems.get(EditMenuItems.UNDO)]);

        menuItems[editMenuItems.get(EditMenuItems.REDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        menuItems[editMenuItems.get(EditMenuItems.REDO)]
                .setOnAction(event -> {
                    final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
                    if (selectedTab instanceof FileEditTab) {
                        ((FileEditTab)selectedTab).redo();
                    }
                });
        menuItems[editMenuItems.get(EditMenuItems.REDO)]
                .setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[editMenuItems.get(EditMenuItems.REDO)]);

        return editMenu;
    }

    /**
     * Initializes the View {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initViewMenu() {
        final Menu viewMenu = new Menu(Messages.getString("KeroEdit.VIEW_MENU"));

        final MenuItem[] menuItems = {new Menu(Messages.getString("KeroEdit.ViewMenu.MAP_ZOOM")),
                                      new Menu(Messages.getString("KeroEdit.ViewMenu.TILESET_ZOOM")),
                                      new MenuItem(Messages.getString("KeroEdit.ViewMenu.TILESET_BG_COLOR"))};
        viewMenu.getItems().addAll(menuItems);

        final EnumMap <ViewMenuItems, Integer> viewMenuItems = new EnumMap <>(ViewMenuItems.class);
        int i = 0;
        for (final ViewMenuItems x : ViewMenuItems.values()) {
            viewMenuItems.put(x, i++);
        }

        final RadioMenuItem[] mapZoomMenuItems = createZoomSubmenu(Config.mapZoom);
        int zoom = 2;
        for (i = 0; i < mapZoomMenuItems.length; ++i) {
            final int z = zoom;
            mapZoomMenuItems[i].setOnAction(event -> {
                Config.mapZoom = z;
                MapEditTab.setMapZoom(z);
            });
            zoom += 2;
        }
        ((Menu)menuItems[viewMenuItems.get(ViewMenuItems.MAP_ZOOM)]).getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = createZoomSubmenu(Config.tilesetZoom);
        zoom = 2;
        for (i = 0; i < tilesetZoomMenuItems.length; ++i) {
            final int z = zoom;
            tilesetZoomMenuItems[i].setOnAction(event -> {
                Config.tilesetZoom = z;
                MapEditTab.setTilesetZoom(z);
            });
            zoom += 2;
        }
        ((Menu)menuItems[viewMenuItems.get(ViewMenuItems.TILESET_ZOOM)]).getItems().addAll(tilesetZoomMenuItems);

        menuItems[viewMenuItems.get(ViewMenuItems.TILESET_BG_COLOR)].setOnAction(event -> {
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

        return viewMenu;
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
    private RadioMenuItem[] createZoomSubmenu(final int currentZoom) {
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
     * Initializes the Actions {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initActionsMenu() {
        final Menu actionsMenu = new Menu(Messages.getString("KeroEdit.ACTIONS_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.getString("KeroEdit.ActionsMenu.RUN_GAME")),
                                      new MenuItem(Messages.getString("KeroEdit.ActionsMenu.EDIT_GLOBAL_SCRIPT")),
                                      new MenuItem(Messages.getString("KeroEdit.ActionsMenu.HACK_EXECUTABLE")),
                                      new MenuItem(Messages.getString("KeroEdit.ActionsMenu.WAFFLE"))};
        actionsMenu.getItems().addAll(menuItems);

        final EnumMap <ActionsMenuItems, Integer> actionsMenuItems = new EnumMap <>(ActionsMenuItems.class);
        int i = 0;
        for (final ActionsMenuItems x : ActionsMenuItems.values()) {
            actionsMenuItems.put(x, i++);
        }

        menuItems[actionsMenuItems.get(ActionsMenuItems.RUN_GAME)].setOnAction(event -> {
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
        menuItems[actionsMenuItems.get(ActionsMenuItems.RUN_GAME)].setDisable(true);
        enableOnLoadItems.add(menuItems[actionsMenuItems.get(ActionsMenuItems.RUN_GAME)]);

        menuItems[actionsMenuItems.get(ActionsMenuItems.EDIT_GLOBAL_SCRIPT)].setOnAction(event -> {
            final FileChooser fChooser = new FileChooser();
            fChooser.setTitle(Messages.getString("KeroEdit.OpenFile.TITLE"));

            fChooser.setInitialDirectory(GameData.getResourceFolder());

            FileChooser.ExtensionFilter[] extensionFilters =
                    {new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.GlobalScript.SCRIPT_FILTER"),
                                                     "*.pxeve"),
                     new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.GlobalScript.NO_FILTER"), "*.*")};
            fChooser.getExtensionFilters().addAll(extensionFilters);
            fChooser.setSelectedExtensionFilter(extensionFilters[0]);

            final File scriptFile = fChooser.showOpenDialog(mainStage);
            if (null != scriptFile) {
                for (final Tab tab : mainTabPane.getTabs()) {
                    if (tab instanceof ScriptEditTab &&
                        tab.getId().equals(scriptFile.getAbsolutePath())) {
                        mainTabPane.getSelectionModel().select(tab);
                        mainTabPane.requestFocus();
                        return;
                    }
                }

                final ScriptEditTab sEditTab = new ScriptEditTab(scriptFile, true);
                mainTabPane.getTabs().add(sEditTab);
                mainTabPane.getSelectionModel().select(sEditTab);
                mainTabPane.requestFocus();
            }
        });
        menuItems[actionsMenuItems.get(ActionsMenuItems.EDIT_GLOBAL_SCRIPT)].setDisable(true);
        enableOnLoadItems.add(menuItems[actionsMenuItems.get(ActionsMenuItems.EDIT_GLOBAL_SCRIPT)]);

        menuItems[actionsMenuItems.get(ActionsMenuItems.HACK_EXECUTABLE)].setOnAction(event -> {
            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof HackTab) {
                    mainTabPane.getSelectionModel().select(tab);
                    mainTabPane.requestFocus();
                    return;
                }
            }

            final HackTab hackTab = new HackTab();
            mainTabPane.getTabs().add(hackTab);
            mainTabPane.getSelectionModel().select(hackTab);
            mainTabPane.requestFocus();
        });
        menuItems[actionsMenuItems.get(ActionsMenuItems.HACK_EXECUTABLE)].setDisable(true);
        enableOnLoadItems.add(menuItems[actionsMenuItems.get(ActionsMenuItems.HACK_EXECUTABLE)]);

        menuItems[actionsMenuItems.get(ActionsMenuItems.WAFFLE)].setOnAction(event -> {
            final Image waffleImg = JavaFXUtil.scaleImage(ResourceManager.getImage("waffle.png"), 16);
            final PrinterJob printJob = PrinterJob.createPrinterJob();
            if (null != printJob) {
                if (printJob.showPrintDialog(mainStage)) {
                    final boolean success = printJob.printPage(new ImageView(waffleImg));
                    if (success) {
                        printJob.endJob();
                    }
                    else {
                        JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                               Messages.getString("KeroEdit.WaffleError.TITLE"), null,
                                               Messages.getString("KeroEdit.WaffleError.MESSAGE"));
                    }
                }
            }
        });

        return actionsMenu;
    }

    /**
     * Initializes the Help menu
     *
     * @return The created {@code Menu}
     */
    private Menu initHelpMenu() {
        final Menu helpMenu = new Menu(Messages.getString("KeroEdit.HELP_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.getString("KeroEdit.HelpMenu.ABOUT")),
                                      new MenuItem(Messages.getString("KeroEdit.HelpMenu.GUIDE"))};
        helpMenu.getItems().addAll(menuItems);

        final EnumMap <HelpMenuItems, Integer> helpMenuItems = new EnumMap <>(HelpMenuItems.class);
        int i = 0;
        for (final HelpMenuItems x : HelpMenuItems.values()) {
            helpMenuItems.put(x, i++);
        }

        menuItems[helpMenuItems.get(HelpMenuItems.ABOUT)].setOnAction(event -> {
            final Alert about = JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                       Messages.getString("KeroEdit.HelpMenu.About.TITLE"), null,
                                                       MessageFormat.format(Messages.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                            Messages.getString("KeroEdit.LAST_UPDATE"),
                                                                            Messages.getString("KeroEdit.VERSION")));

            about.getDialogPane().setGraphic(new ImageView(ResourceManager.getImage("fdl_logo.png")));
            about.showAndWait();
        });

        menuItems[helpMenuItems.get(HelpMenuItems.GUIDE)]
                .setOnAction(event -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                             Messages.getString("KeroEdit.HelpMenu.GUIDE").replace("_", ""),
                                                             null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                .showAndWait());
        return helpMenu;
    }

    /**
     * Creates the map list {@code ListView}, but does not put any maps into it.
     *
     * @return The created {@code ListView}
     */
    private ListView initMapList() {
        mapList = new ListView <>();
        mapList.setOrientation(Orientation.VERTICAL);

        mapList.setMinWidth(125);
        mapList.setPrefWidth(125);

        final MenuItem[] contextMenuItems = {new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.OPEN_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DELETE_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP"))};

        final ContextMenu contextMenu = new ContextMenu(contextMenuItems);
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
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)].setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)].setOnAction(event -> {
            final String filename = mapList.getSelectionModel().getSelectedItem();
            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof MapEditTab &&
                    tab.getId().equals(filename)) {
                    mainTabPane.getSelectionModel().select(tab);
                    mainTabPane.requestFocus();
                    return;
                }
            }

            final MapEditTab mapEditTab = new MapEditTab(filename);
            mainTabPane.getTabs().add(mapEditTab);
            mainTabPane.getSelectionModel().select(mapEditTab);
            mainTabPane.requestFocus();
        });
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)]);

        contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)]
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)].setOnAction(event -> {
            JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                   Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP").replace("_", ""),
                                   null, Messages.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait();
            //TODO: When I implement this, use Task
            /*File map;
            for (int j = 0; ; ++j) {
                map = new File(GameData.getResourceFolder().getAbsolutePath() +
                             File.separatorChar + "field" + File.separatorChar + "newmap" + j + ".pxpack");
                if (!map.exists()) {

                }
            }*/
        });
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)]);

        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)].setOnAction(event -> {
            final String filename = mapList.getSelectionModel().getSelectedItem();
            GameData.removeMap(filename);
            mapList.getItems().remove(filename);

            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof MapEditTab &&
                    tab.getId().equals(filename)) {
                    mainTabPane.getTabs().remove(tab);
                    break;
                }
            }
        });
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)]);

        contextMenuItems[mapListMenuItems.get(MapListMenuItems.RENAME)]
                .setOnAction(event ->
                                     JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                            Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")
                                                                    .replace("_", ""),
                                                            null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                               .showAndWait());
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.RENAME)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.RENAME)]);

        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DUPLICATE)]
                .setOnAction(event ->
                                     //TODO: Create prompt for new mapname
                                     JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                            Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP")
                                                                    .replace("_", ""),
                                                            null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                               .showAndWait());
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DUPLICATE)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.DUPLICATE)]);

        mapList.setOnKeyPressed(event -> {
            //Retrieves the index of the MenuItem in the context menu that the key is tied to
            final int menuItemIndex = contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)]
                                              .getAccelerator().match(event) ?
                                      mapListMenuItems.get(MapListMenuItems.OPEN) :

                                      contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)]
                                              .getAccelerator().match(event) ?
                                      mapListMenuItems.get(MapListMenuItems.NEW) :

                                      contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)]
                                              .getAccelerator().match(event) ?
                                      mapListMenuItems.get(MapListMenuItems.DELETE) : -1;

            if (-1 != menuItemIndex) {
                contextMenu.getItems().get(menuItemIndex).getOnAction().handle(new ActionEvent());
            }
        });

        mapList.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()) {
                contextMenu.getItems().get(mapListMenuItems.get(MapListMenuItems.OPEN))
                           .getOnAction().handle(new ActionEvent());
            }
        });

        return mapList;
    }

    /**
     * Adds all the maps to the {@code ListView} for maps that was previously created by {@code setupMapList()}.
     */
    private void loadMapList() {
        mapList.setItems(FXCollections.observableArrayList(GameData.getMapList()));
        mapList.requestFocus();
    }

    /**
     * Initializes the {@code TabPane} that forms the core of the KeroEdit program.
     *
     * @return The created {@code TabPane}
     */
    private TabPane initTabPane() {
        mainTabPane = new TabPane();

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);

        notepadTab = new Tab(Messages.getString("KeroEdit.NOTEPAD_TITLE"));
        notepadTab.setId(Messages.getString("KeroEdit.NOTEPAD_TITLE"));

        notepadTab.setContent(new TextArea(Config.notepadText));

        notepadTab.setClosable(false);
        mainTabPane.getTabs().add(notepadTab);

        return mainTabPane;
    }

    /**
     * Prompts the user, asking them if they agree to the license terms (Apache 2.0).
     * Also sets {@code Config.licenseRead}.
     */
    private void showLicense() {
        final File licenseFile = ResourceManager.getFile("LICENSE");
        final char[] chars = new char[(int)licenseFile.length()];
        try {
            if (0 < new FileReader(licenseFile).read(chars)) {
                final String licenseText = new String(chars);
                final Alert licenseAlert = JavaFXUtil.createTextboxAlert(Alert.AlertType.CONFIRMATION,
                                                                         Messages.getString("KeroEdit.ReadLicense.TITLE"),
                                                                         null,
                                                                         Messages.getString("KeroEdit.ReadLicense.MESSAGE"),
                                                                         licenseText, false);
                licenseAlert.showAndWait().ifPresent(result -> Config.licenseRead = ButtonType.OK == result);
                return;
            }
        }
        catch (final IOException except) {

        }
        JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                               Messages.getString("KeroEdit.ReadLicense.UnableToShow.TITLE"), null,
                               Messages.getString("KeroEdit.ReadLicense.UnableToShow.MESSAGE")).showAndWait();
    }

    private static class SettingsPane extends GridPane {
        SettingsPane() {
            setPadding(new Insets(10, 10, 10, 10));
            setVgap(10);
            setHgap(20);

            int x = 0;
            initDisplayedLayers(x++, 0);
            initSelectedLayer(x++, 0);
            initDrawModes(x++, 0);
            initViewSettings(x++, 0);
        }

        private void initDisplayedLayers(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.DISPLAYED_LAYERS"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final CheckBox[] checkboxes = new CheckBox[PxPack.NUM_LAYERS];
            for (int i = 0; i < checkboxes.length; ++i) {
                final String layerName;
                switch (i) {
                    case 0:
                        layerName = Messages.getString("LayerNames.FOREGROUND");
                        break;
                    case 1:
                        layerName = Messages.getString("LayerNames.MIDDLEGROUND");
                        break;
                    default:
                        layerName = Messages.getString("LayerNames.BACKGROUND");
                }
                checkboxes[i] = new CheckBox(layerName);

                checkboxes[i].setAllowIndeterminate(false);
                checkboxes[i].setSelected(true);
                MapEditTab.bindDisplayedLayer(i, checkboxes[i].selectedProperty());

                add(checkboxes[i], x, y++);
            }
        }

        private void initSelectedLayer(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.SELECTED_LAYER"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = new RadioButton[PxPack.NUM_LAYERS];

            final String[] layerNames = {Messages.getString("LayerNames.FOREGROUND"),
                                         Messages.getString("LayerNames.MIDDLEGROUND"),
                                         Messages.getString("LayerNames.BACKGROUND")};

            for (int i = 0; i < radioButtons.length; ++i) {
                radioButtons[i] = new RadioButton(layerNames[i]);

                radioButtons[i].setToggleGroup(toggleGroup);

                if (0 == i) {
                    radioButtons[i].setSelected(true);
                }

                final int layer = i;
                radioButtons[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        MapEditTab.setSelectedLayer(layer);
                    }
                });

                add(radioButtons[i], x, y++);
            }
        }

        private void initDrawModes(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.DRAW_MODES"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.getString("KeroEdit.SettingsPane.DRAW"))};

            final EnumMap <DrawSettingsItems, Integer> drawSettingsItems = new EnumMap <>(DrawSettingsItems.class);
            int i = 0;
            for (final DrawSettingsItems k : DrawSettingsItems.values()) {
                drawSettingsItems.put(k, i++);
            }

            radioButtons[drawSettingsItems.get(DrawSettingsItems.DRAW)].setSelected(true);

            for (i = 0; i < radioButtons.length; ++i) {
                final int mode = i;
                radioButtons[i].setOnAction(event -> MapEditTab.setDrawMode(DrawSettingsItems.values()[mode]));
                radioButtons[i].setToggleGroup(toggleGroup);
                add(radioButtons[i], x, y++);
            }
        }

        private void initViewSettings(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.VIEW_SETTINGS"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final CheckBox[] toggles = {new CheckBox(Messages.getString("KeroEdit.SettingsPane.SHOW_TILE_TYPES"))};

            final EnumMap <ViewSettingsItems, Integer> viewSettingsItems = new EnumMap <>(ViewSettingsItems.class);
            int i = 0;
            for (final ViewSettingsItems k : ViewSettingsItems.values()) {
                viewSettingsItems.put(k, i++);
            }

            toggles[viewSettingsItems.get(ViewSettingsItems.TILE_TYPES)].setSelected(false);
            MapEditTab.bindShowTileTypes(toggles[viewSettingsItems.get(ViewSettingsItems.TILE_TYPES)].selectedProperty());

            for (final CheckBox toggle : toggles) {
                add(toggle, x, y++);
            }

        }
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
        RUN_GAME,
        EDIT_GLOBAL_SCRIPT,
        HACK_EXECUTABLE,
        WAFFLE
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

    public enum DrawSettingsItems {
        DRAW,
        RECT,
        COPY,
        FILL,
        REPLACE
    }

    private enum ViewSettingsItems {
        TILE_TYPES
    }
}