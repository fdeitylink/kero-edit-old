/*
 * TODO:
 * Barebones PNG editor
 *  - Color picker
 *  - Canvas
 *  - Reload tilesets in open maps on save (or on edit?)
 * Ctrl +/- and scroll wheel for zoom
 * Resort map ListView alphabetically when map is added, and select and open the new map
 * Lower memory usage and stuffs
 * Play pxtone files (javafx.scene.media? javax.sound.sampled?)
 * In script editor, eventually put in an autocompleter for stuff like entity names
 * Add @throws to JavaDoc comments for runtime exceptions
 * Verify default uncaught exception handler works
 * Throw EOFExceptions if EOF met during PXPACK or PXATTR parsing (chan.read() returns -1 if it met EOF)
 *   - Create wrapper channel class that throws EOFException?
 * Allow drag/drop in mod executable? (other files?)
 * Acquire FileLocks on files being read and saved?
 * Use direct ByteBuffer? (only for EXE?)
 * Break up messages.properties into individual files? (one for each class or package)
 * Throw OutOfBoundsExceptions instead of IllegalArgumentExceptions for values outside ranges?
 * LRU cache for ImageManager and PxAttrManager?
 * Use temp variables in for/for-each loops rather than repeatedly calling method
 * Use WatchService in GameData (and in PxAttr/ImageManager?)
 * Why is the scrollbar sometimes super tiny on the mapListView?
 * App icon (also use it for child windows)
 * Make sure all Alert creations are accompanied by showAndWait() calls (some are missing)
 * Have any empty catch blocks log the exception with Logger.logThrowable()
 * Keep any MenuItems with unimplemented features disabled (like what I did for HackTab)
 * Use TornadoFX after converting to Kotlin?
 * Put kotlin-reflect.jar on the classpath (bundle into JAR?)
 *  - Is this still necessary as of Kotlin 1.1? (https://kotlinlang.org/docs/reference/whatsnew11.html#kotlinreflectfull)
 * Use Gradle (Kobalt?)
 * Turn members of Kotlin objects into top-level properties and functions?
 * Don't show tileset stage until after main window is showing (if it is set to be shown from the last session)
 * Before converting MapEditTab, rename its messages.properties strings to match the class hierarchy better
 *  - Also improve the string names in general
 *  - Decide whether to always pass full file path or just filename to the messages
 * ImmutableArray class?
 * Use except.localizedMessage for exceptions that may be visible to the user
 */

package io.fdeitylink.keroedit;

import java.util.List;
import java.util.ArrayList;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import java.text.MessageFormat;

import java.io.File;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.io.IOException;
import java.text.ParseException;

import java.nio.file.DirectoryStream;

import java.nio.charset.Charset;

import javafx.application.Application;
import javafx.application.Platform;

import javafx.stage.Stage;
import javafx.scene.control.Dialog;
import javafx.stage.Screen;
import javafx.scene.Scene;

import javafx.geometry.Rectangle2D;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;

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
import javafx.scene.control.ColorPicker;

import javafx.collections.ObservableList;

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
import javafx.scene.image.ImageView;

import io.fdeitylink.util.NullArgumentException;

import io.fdeitylink.util.UtilsKt;

import io.fdeitylink.util.Logger;

import io.fdeitylink.util.SafeEnum;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.util.fx.FXUtil;

import io.fdeitylink.keroedit.gamedata.GameData;

import io.fdeitylink.keroedit.image.ImageManager;
import io.fdeitylink.keroedit.image.PxAttrManager;

import io.fdeitylink.util.fx.FileEditTab;

import io.fdeitylink.keroedit.hack.HackTab;
import io.fdeitylink.keroedit.mapedit.MapEditTab;
import io.fdeitylink.keroedit.script.ScriptEditTab;

public final class KeroEdit extends Application {
    private static KeroEdit INSTANCE;

    private static final String baseTitleStr = MessageFormat.format(Messages.INSTANCE.getString("KeroEdit.APP_TITLE"),
                                                                    Messages.INSTANCE.getString("KeroEdit.VERSION"));
    private ArrayList <MenuItem> enableOnLoadItems;

    private Stage mainStage;
    private TabPane mainTabPane;

    private MenuItem openLast;

    private NotepadTab notepadTab;

    private ListView <Path> mapList;

    public static void main(final String[] args) {
        launch(args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final Stage primaryStage) {
        INSTANCE = this;

        //TODO: The handler isn't being executed on non-FX Application threads...
        final Thread.UncaughtExceptionHandler fxDefExceptHandler = Thread.currentThread().getUncaughtExceptionHandler();
        final Thread.UncaughtExceptionHandler exceptHandler = (thread, throwable) -> {
            fxDefExceptHandler.uncaughtException(thread, throwable);
            Logger.INSTANCE.logThrowable("Uncaught exception: ", throwable);
        };
        Thread.currentThread().setUncaughtExceptionHandler(exceptHandler);
        Thread.setDefaultUncaughtExceptionHandler(exceptHandler);

        Config.INSTANCE.load();

        mainStage = primaryStage;
        mainStage.setOnCloseRequest(event -> {
            //if user didn't cancel any attempted tab closes and definitely wants to close program
            if (!closeTabs()) {
                Config.notepadText = notepadTab.notepad.getText();
                Config.INSTANCE.save();

                Platform.exit(); //graceful shutdown & closes all child windows
            }

            event.consume();
        });

        enableOnLoadItems = new ArrayList <>();

        /*
         * Note to self - keep these as BorderPanes - while a VBox may conceptually seem
         * more fit for this it does not resize well and there's a whole load of sizing issues.
         */
        final BorderPane right = new BorderPane(mainTabPane = initTabPane());
        right.setTop(new SettingsPane());

        final SplitPane sPane = new SplitPane(mapList = initMapList(), right);
        sPane.setDividerPositions(0.1);

        final BorderPane root = new BorderPane(sPane);
        root.setTop(initMenuBar());

        final Rectangle2D displayRect = Screen.getPrimary().getVisualBounds();
        mainStage.setScene(new Scene(root, displayRect.getWidth(), displayRect.getHeight()));
        setTitle("");

        mainStage.show();
        mainStage.setMaximized(true);
        mainStage.requestFocus();

        showLicense();

        /* ****************************************** Testing PoppableTab ******************************************* */
        /*final ImageView node = new ImageView(ResourceManager.INSTANCE.getImage("fdl_logo.png"));
        final PoppableTab tab = new PoppableTab("Test Poppable Tab", node);
        tab.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!tab.isSelected()) {
                tab.setContent(null);
                tab.setText("");
            }
            else {
                tab.setContent(node);
                tab.setText("Test Poppable Tab");
            }
        });
        mainTabPane.getTabs().add(tab);*/
    }

    /**
     * Appends a given string to the base title of the program's {@code Stage}
     *
     * @param str The string to append to the title
     */
    public static void setTitle(final String str) {
        INSTANCE.mainStage.setTitle(baseTitleStr + str);
    }

    /**
     * Returns the {@code String} that has been appended to the base title of the program's {@code Stage}
     *
     * @return the {@code String} that has been appended to the base title of the program's {@code Stage}
     */
    public static String getTitle() {
        final String fullTitle = INSTANCE.mainStage.getTitle();
        return null == fullTitle ? "" : fullTitle.substring(baseTitleStr.length());
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
        final Menu fileMenu = new Menu(Messages.INSTANCE.getString("KeroEdit.FILE_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.INSTANCE.getString("KeroEdit.FileMenu.OPEN")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.FileMenu.OPEN_LAST")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.FileMenu.SAVE")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.FileMenu.SAVE_ALL")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.FileMenu.CLOSE_TAB")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.FileMenu.CLOSE_ALL_TABS"))};
        fileMenu.getItems().addAll(menuItems);

        menuItems[FileMenuItem.OPEN.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.OPEN.ordinal()].setOnAction(event -> {
            //if user didn't cancel any attempted tab closes and definitely wants to load new mod
            if (!closeTabs()) {
                wipeLoaded();

                final FileChooser exeChooser = new FileChooser();
                exeChooser.setTitle(Messages.INSTANCE.getString("KeroEdit.OpenMod.TITLE"));

                /*final String exeLoc = Config.INSTANCE.getLastExeLoc();
                final String initDir = exeLoc.substring(0, exeLoc.lastIndexOf(File.separatorChar) + 1);*/
                exeChooser.setInitialDirectory(Config.INSTANCE.getLastExeLoc().getParent().toFile());

                final FileChooser.ExtensionFilter extFilter =
                        new FileChooser.ExtensionFilter(Messages.INSTANCE.getString("KeroEdit.OpenMod.EXECUTABLE_FILTER"), "*.exe");
                exeChooser.getExtensionFilters().add(extFilter);

                final File exeFile = exeChooser.showOpenDialog(mainStage);
                if (null != exeFile) {
                    loadMod(exeFile.toPath().toAbsolutePath());
                }
            }
        });

        menuItems[FileMenuItem.OPEN_LAST.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.OPEN_LAST.ordinal()].setOnAction(event -> {
            //if user didn't cancel any attempted tab closes and definitely wants to load last mod
            if (!closeTabs()) {
                wipeLoaded();
                loadMod(Config.INSTANCE.getLastExeLoc().toAbsolutePath());
            }
        });
        //Disabled if there is no last mod
        menuItems[FileMenuItem.OPEN_LAST.ordinal()].setDisable(!Config.INSTANCE.getLastExeLoc().toString().endsWith(".exe"));
        openLast = menuItems[FileMenuItem.OPEN_LAST.ordinal()];

        menuItems[FileMenuItem.SAVE.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.SAVE.ordinal()].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FileEditTab) {
                ((FileEditTab)selectedTab).save();
            }
        });
        menuItems[FileMenuItem.SAVE.ordinal()].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.SAVE.ordinal()]);

        menuItems[FileMenuItem.SAVE_ALL.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.SAVE_ALL.ordinal()].setOnAction(event -> {
            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof FileEditTab) {
                    ((FileEditTab)tab).save();
                }
            }
        });
        menuItems[FileMenuItem.SAVE_ALL.ordinal()].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.SAVE_ALL.ordinal()]);

        menuItems[FileMenuItem.CLOSE_TAB.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.CLOSE_TAB.ordinal()].setOnAction(event -> {
            final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            if (-1 != tabIndex && mainTabPane.getTabs().get(tabIndex) != notepadTab) {
                FXUtil.INSTANCE.close(mainTabPane.getTabs().get(tabIndex));
            }
        });
        menuItems[FileMenuItem.CLOSE_TAB.ordinal()].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.CLOSE_TAB.ordinal()]);

        menuItems[FileMenuItem.CLOSE_ALL_TABS.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.CLOSE_ALL_TABS.ordinal()].setOnAction(event -> closeTabs());
        menuItems[FileMenuItem.CLOSE_ALL_TABS.ordinal()].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.CLOSE_ALL_TABS.ordinal()]);

        return fileMenu;
    }

    /**
     * Erases all stored and open remnants of the currently loaded mod.
     * By now the user should have saved or discarded their work and
     * confirmed they want to load a new mod.
     */
    private void wipeLoaded() {
        setTitle("");

        for (final MenuItem mItem : enableOnLoadItems) {
            if (!mItem.isDisable()) {
                mItem.setDisable(true);
            }
        }

        GameData.INSTANCE.wipe();
        HackTab.INSTANCE.wipe();
        ImageManager.INSTANCE.wipe();
        PxAttrManager.INSTANCE.wipe();
        MapEditTab.wipeResources();
    }

    /**
     * Loads a mod, checking if it is valid. Also creates its assist folder.
     *
     * @param executable A {@code Path} that references the exe for a mod.
     *
     * @throws NullArgumentException if {@code executable} is null
     */
    private void loadMod(final Path executable) {
        final Path exe = NullArgumentException.Companion.requireNonNull(executable, "loadMod", "executable").toAbsolutePath();
        try {
            FXUtil.INSTANCE.task(() -> {
                if (!exe.toString().endsWith(".exe")) {
                    Platform.runLater(() -> FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                                        Messages.INSTANCE.getString("KeroEdit.LoadMod.NotExe.TITLE"),
                                                                        null,
                                                                        MessageFormat.format(Messages.INSTANCE.getString("KeroEdit.LoadMod.NotExe.MESSAGE"),
                                                                                             exe.toAbsolutePath())).showAndWait());
                    return null;
                }

                try {
                    GameData.INSTANCE.init(exe);
                    Config.INSTANCE.setLastExeLoc(exe);

                    final File executableParent = exe.getParent().toAbsolutePath().toFile();
                    boolean hasRWXPermissions = executableParent.canRead() && executableParent.canWrite() &&
                                                executableParent.canExecute();
                    if (!hasRWXPermissions) {
                        if (!(hasRWXPermissions = executableParent.setReadable(true) &&
                                                  executableParent.setWritable(true) &&
                                                  executableParent.setExecutable(true))) {
                            Platform.runLater(() -> FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                                                                Messages.INSTANCE.getString("KeroEdit.LoadMod.StrictPermissions.TITLE"),
                                                                                null,
                                                                                Messages.INSTANCE.getString("KeroEdit.LoadMod.StrictPermissions.MESSAGE"))
                                                                   .showAndWait());
                        }
                    }
                    if (hasRWXPermissions) {
                        createAssistFolder();
                    }

                    HackTab.INSTANCE.init();

                    Platform.runLater(() -> {
                        setTitle(" - " + exe.getParent().toAbsolutePath() + File.separatorChar);

                        openLast.setDisable(false);

                        mapList.setItems(GameData.INSTANCE.getMaps());
                        mapList.requestFocus();

                        for (final MenuItem mItem : enableOnLoadItems) {
                            if (mItem.isDisable()) {
                                mItem.setDisable(false);
                            }
                        }
                    });
                }
                catch (final IOException except) {
                    Platform.runLater(() -> FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                                        Messages.INSTANCE.getString("KeroEdit.LoadMod.IOExcept.TITLE"),
                                                                        null, except.getMessage()).showAndWait());
                }

                return null;
            }).run();
            //TODO: Use Service subclass
        }
        catch (final Exception except) {
            Logger.INSTANCE.logThrowable("Error during mod loading", except);
        }
    }

    /**
     * Creates the assist folder for a mod inside of its resource folder
     */
    private void createAssistFolder() {
        try {
            final Path modAssistPath = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                                 File.separatorChar + "assist");
            if (!Files.exists(modAssistPath)) {
                Files.createDirectory(modAssistPath);
            }

            final Path internalAssistPath = ResourceManager.INSTANCE.getPath("assist");
            if (null == internalAssistPath) {
                throw new IOException(); //jumps to outer catch block with CopyFolderFail
            }

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

            final DirectoryStream <Path> assistPaths = Files.newDirectoryStream(internalAssistPath);
            for (final Path p : assistPaths) {
                //skip if wrong *_strings.json file or if unittype.txt (not really necessary anymore)
                if ((p.toString().endsWith("_strings.json") && !p.getFileName().toString().equals(stringsFname)) ||
                    "unittype.txt".equals(p.getFileName().toString())) {
                    continue;
                }

                try {
                    final Path modCopyPath = Paths.get(modAssistPath.toString() + File.separatorChar + p.getFileName());
                    if (!Files.exists(modCopyPath)) {
                        Files.copy(p, modCopyPath);
                    }
                }
                catch (final IOException except) {
                    FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                Messages.INSTANCE.getString("KeroEdit.CreateAssist.CopyFileFail.TITLE"), null,
                                                MessageFormat.format(Messages.INSTANCE.getString("KeroEdit.CreateAssist.CopyFileFail.MESSAGE"),
                                                                     p.getFileName())).showAndWait();
                    //TODO: skip the rest of the files?
                }
            }
        }
        catch (final IOException except) {
            FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                        Messages.INSTANCE.getString("KeroEdit.CreateAssist.CopyFolderFail.TITLE"), null,
                                        Messages.INSTANCE.getString("KeroEdit.CreateAssist.CopyFolderFail.MESSAGE")).showAndWait();
        }
    }

    /**
     * Initializes the Edit {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initEditMenu() {
        final Menu editMenu = new Menu(Messages.INSTANCE.getString("KeroEdit.EDIT_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.INSTANCE.getString("KeroEdit.EditMenu.UNDO")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.EditMenu.REDO"))};
        editMenu.getItems().addAll(menuItems);

        menuItems[EditMenuItem.UNDO.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        menuItems[EditMenuItem.UNDO.ordinal()].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FileEditTab) {
                ((FileEditTab)selectedTab).undo();
            }
        });
        menuItems[EditMenuItem.UNDO.ordinal()].setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[EditMenuItem.UNDO.ordinal()]);

        menuItems[EditMenuItem.REDO.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        menuItems[EditMenuItem.REDO.ordinal()].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FileEditTab) {
                ((FileEditTab)selectedTab).redo();
            }
        });
        menuItems[EditMenuItem.REDO.ordinal()].setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[EditMenuItem.REDO.ordinal()]);

        return editMenu;
    }

    /**
     * Initializes the View {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initViewMenu() {
        final Menu viewMenu = new Menu(Messages.INSTANCE.getString("KeroEdit.VIEW_MENU"));

        final MenuItem[] menuItems = {new Menu(Messages.INSTANCE.getString("KeroEdit.ViewMenu.MAP_ZOOM")),
                                      new Menu(Messages.INSTANCE.getString("KeroEdit.ViewMenu.TILESET_ZOOM")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.ViewMenu.TILESET_BG_COLOR"))};
        viewMenu.getItems().addAll(menuItems);

        final RadioMenuItem[] mapZoomMenuItems = buildZoomSubmenu(Config.INSTANCE.getMapZoom());

        double zoom = 0.5;
        for (final RadioMenuItem mapZoomMItem : mapZoomMenuItems) {
            final double z = zoom;
            Config.INSTANCE.setMapZoom(z);
            mapZoomMItem.setOnAction(event -> MapEditTab.setMapZoom(z));
            zoom += 0.5;
        }
        ((Menu)menuItems[ViewMenuItem.MAP_ZOOM.ordinal()]).getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = buildZoomSubmenu(Config.INSTANCE.getTilesetZoom());
        zoom = 0.5;
        for (final RadioMenuItem tilesetZoomMItem : tilesetZoomMenuItems) {
            final double z = zoom;
            Config.INSTANCE.setTilesetZoom(z);
            tilesetZoomMItem.setOnAction(event -> MapEditTab.setTilesetZoom(z));
            zoom += 0.5;
        }
        ((Menu)menuItems[ViewMenuItem.TILESET_ZOOM.ordinal()]).getItems().addAll(tilesetZoomMenuItems);

        menuItems[ViewMenuItem.TILESET_BG_COLOR.ordinal()].setOnAction(event -> {
            final ColorPicker cPicker = new ColorPicker(Config.tilesetBgColor);
            cPicker.setOnAction(ev -> MapEditTab.setTilesetBgColor(Config.tilesetBgColor = cPicker.getValue()));

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
     * @param defaultZoom The current/default zoom level, the {@code RadioMenuItem} for which
     *                    will be selected.
     *
     * @return An array of {@code RadioMenuItem}s in a {@code ToggleGroup} that can be used
     * to set a zoom level. None of them are bound to an {@code EventHandler <ActionEvent>},
     * so that must be done after they are created
     */
    private RadioMenuItem[] buildZoomSubmenu(final double defaultZoom) {
        final ToggleGroup toggleGroup = new ToggleGroup();
        final RadioMenuItem[] zoomMenuItems = new RadioMenuItem[8];

        double zoom = 0.5;
        for (int i = 0; i < zoomMenuItems.length; ++i) {
            zoomMenuItems[i] = new RadioMenuItem(Integer.toString((int)(zoom * 100)) + '%');
            zoomMenuItems[i].setToggleGroup(toggleGroup);
            zoomMenuItems[i].setSelected(defaultZoom == zoom);

            zoom += 0.5;
        }

        return zoomMenuItems;
    }

    /**
     * Initializes the Actions {@code Menu}
     *
     * @return The created {@code Menu}
     */
    private Menu initActionsMenu() {
        final Menu actionsMenu = new Menu(Messages.INSTANCE.getString("KeroEdit.ACTIONS_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.INSTANCE.getString("KeroEdit.ActionsMenu.RUN_GAME")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.ActionsMenu.EDIT_GLOBAL_SCRIPT")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.ActionsMenu.HACK_EXECUTABLE")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.ActionsMenu.WAFFLE"))};
        actionsMenu.getItems().addAll(menuItems);

        menuItems[ActionsMenuItem.RUN_GAME.ordinal()].setOnAction(event -> {
            try {
                //TODO: Prompt to save unsaved tabs before running?
                Runtime.getRuntime().exec(GameData.INSTANCE.getExecutable().toString());
            }
            catch (final IOException except) {
                FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                            Messages.INSTANCE.getString("KeroEdit.RunGame.IOExcept.TITLE"), null,
                                            Messages.INSTANCE.getString("KeroEdit.RunGame.IOExcept.MESSAGE")).showAndWait();
            }
        });
        menuItems[ActionsMenuItem.RUN_GAME.ordinal()].setDisable(true);
        enableOnLoadItems.add(menuItems[ActionsMenuItem.RUN_GAME.ordinal()]);

        menuItems[ActionsMenuItem.EDIT_GLOBAL_SCRIPT.ordinal()].setOnAction(event -> {
            final FileChooser scrChooser = new FileChooser();
            scrChooser.setTitle(Messages.INSTANCE.getString("KeroEdit.GlobalScript.TITLE"));

            scrChooser.setInitialDirectory(new File(GameData.INSTANCE.getResourceFolder().toString()));

            final FileChooser.ExtensionFilter[] extFilters =
                    {new FileChooser.ExtensionFilter(Messages.INSTANCE.getString("KeroEdit.GlobalScript.SCRIPT_FILTER"), "*.pxeve"),
                     new FileChooser.ExtensionFilter(Messages.INSTANCE.getString("KeroEdit.GlobalScript.NO_FILTER"), "*.*")};
            scrChooser.getExtensionFilters().addAll(extFilters);
            scrChooser.setSelectedExtensionFilter(extFilters[0]);

            final List <File> scriptFiles = scrChooser.showOpenMultipleDialog(mainStage);
            if (null != scriptFiles) {
                for (final File f : scriptFiles) {
                    final Path scriptPath = f.toPath().toAbsolutePath();
                    boolean isAlreadyOpen = false;

                    final ObservableList <Tab> tabs = mainTabPane.getTabs();
                    for (final Tab tab : tabs) {
                        if ((tab instanceof ScriptEditTab && ((ScriptEditTab)tab).getPath().equals(scriptPath)) ||
                            (tab instanceof MapEditTab && ((MapEditTab)tab).getScriptPath().equals(scriptPath))) {
                            mainTabPane.getSelectionModel().select(tab);
                            mainTabPane.requestFocus(); //TODO: Select ScriptEditTab in the MapEditTab?
                            isAlreadyOpen = true;
                            break;
                        }
                    }

                    if (!isAlreadyOpen) {
                        try {
                            final ScriptEditTab sEditTab = new ScriptEditTab(scriptPath);

                            mainTabPane.getTabs().add(sEditTab);
                            mainTabPane.getSelectionModel().select(sEditTab);
                            mainTabPane.requestFocus();
                        }
                        catch (final IOException except) {
                            /*
                             * Do nothing - exception just signals that there was a script reading issue
                             * and prevents us from adding the ScriptEditTab to the tab pane.
                             * A dialog was already shown to the user via the ScriptEditTab constructor.
                             */
                        }
                    }
                }
            }
        });
        menuItems[ActionsMenuItem.EDIT_GLOBAL_SCRIPT.ordinal()].setDisable(true);
        enableOnLoadItems.add(menuItems[ActionsMenuItem.EDIT_GLOBAL_SCRIPT.ordinal()]);

        //Disabled until I give it more functionality and the ability to load and save the executable
        /*menuItems[ActionsMenuItem.HACK_EXECUTABLE.ordinal()].setOnAction(event -> {
            if (!mainTabPane.getTabs().contains(HackTab.INSTANCE)) {
                mainTabPane.getTabs().add(HackTab.INSTANCE);
            }
            mainTabPane.getSelectionModel().select(HackTab.INSTANCE);
            mainTabPane.requestFocus();
        });
        enableOnLoadItems.add(menuItems[ActionsMenuItem.HACK_EXECUTABLE.ordinal()]);*/
        menuItems[ActionsMenuItem.HACK_EXECUTABLE.ordinal()].setDisable(true);

        menuItems[ActionsMenuItem.WAFFLE.ordinal()].setOnAction(event -> {
            final Alert errorAlert = FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                                 Messages.INSTANCE.getString("KeroEdit.WaffleError.TITLE"), null,
                                                                 Messages.INSTANCE.getString("KeroEdit.WaffleError.MESSAGE"));
            final PrinterJob printJob = PrinterJob.createPrinterJob();

            if (null == printJob) {
                errorAlert.showAndWait();
                return;
            }

            //TODO: Print preview, etc.?
            if (printJob.showPrintDialog(mainStage)) {
                if (printJob.printPage(new ImageView(FXUtil.INSTANCE.scale(ResourceManager.INSTANCE.getImage("waffle.png"), 32)))) {
                    printJob.endJob();
                }
                else {
                    errorAlert.showAndWait();
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
        final Menu helpMenu = new Menu(Messages.INSTANCE.getString("KeroEdit.HELP_MENU"));

        final MenuItem[] menuItems = {new MenuItem(Messages.INSTANCE.getString("KeroEdit.HelpMenu.ABOUT")),
                                      new MenuItem(Messages.INSTANCE.getString("KeroEdit.HelpMenu.GUIDE"))};
        helpMenu.getItems().addAll(menuItems);

        menuItems[HelpMenuItem.ABOUT.ordinal()].setOnAction(event -> {
            final Alert aboutAlert = FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                                                 Messages.INSTANCE.getString("KeroEdit.HelpMenu.About.TITLE"), null,
                                                                 MessageFormat.format(Messages.INSTANCE.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                                      Messages.INSTANCE.getString("KeroEdit.LAST_UPDATE"),
                                                                                      Messages.INSTANCE.getString("KeroEdit.VERSION")));

            aboutAlert.setGraphic(new ImageView(ResourceManager.INSTANCE.getImage("fdl_logo.png")));
            aboutAlert.showAndWait();
        });

        menuItems[HelpMenuItem.GUIDE.ordinal()]
                .setOnAction(event -> FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                                                  Messages.INSTANCE.getString("KeroEdit.HelpMenu.GUIDE").replace("_", ""),
                                                                  null, Messages.INSTANCE.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                     .showAndWait());
        return helpMenu;
    }

    /**
     * Creates the map list {@code ListView}, but does not put any maps into it.
     *
     * @return The created {@code ListView}
     */
    private ListView <Path> initMapList() {
        //TODO: Make cells editable (and rename maps after user is done editing cell)?
        final ListView <Path> mapListView = new ListView <>();
        mapListView.setOrientation(Orientation.VERTICAL);
        mapListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        mapListView.setCellFactory(listView -> new ListCell <Path>() {
            @Override
            public void updateItem(final Path map, final boolean empty) {
                super.updateItem(map, empty);
                setText(empty ? null : UtilsKt.baseFilename(map, GameData.mapExtension));
            }
        });

        final MenuItem[] contextMenuItems = {new MenuItem(Messages.INSTANCE.getString("KeroEdit.MapList.OPEN")),
                                             new MenuItem(Messages.INSTANCE.getString("KeroEdit.MapList.NEW")),
                                             new MenuItem(Messages.INSTANCE.getString("KeroEdit.MapList.DELETE")),
                                             new MenuItem(Messages.INSTANCE.getString("KeroEdit.MapList.RENAME")),
                                             new MenuItem(Messages.INSTANCE.getString("KeroEdit.MapList.DUPLICATE"))};

        mapListView.setContextMenu(new ContextMenu(contextMenuItems));

        //TODO: Change accelerator text from an arrow to "Enter"
        contextMenuItems[MapListMenuItem.OPEN.ordinal()].setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuItems[MapListMenuItem.OPEN.ordinal()].setOnAction(event -> {
            final ObservableList <Path> selectedItems = mapListView.getSelectionModel().getSelectedItems();
            for (final Path map : selectedItems) {
                boolean isAlreadyOpen = false;
                for (final Tab tab : mainTabPane.getTabs()) {
                    if (tab instanceof MapEditTab && ((MapEditTab)tab).getPath().equals(map)) {
                        mainTabPane.getSelectionModel().select(tab);
                        mainTabPane.requestFocus();
                        isAlreadyOpen = true;
                        break;
                    }
                }

                if (!isAlreadyOpen) {
                    try {
                        /*
                         * If the script for this map is already open in mainTabPane, close it so that
                         * two copies aren't open. The map's filename (sans extension) is equivalent to
                         * the script's filename (sans extension)
                         */
                        for (final Tab tab : mainTabPane.getTabs()) {
                            if (tab instanceof ScriptEditTab &&
                                UtilsKt.baseFilename(((ScriptEditTab)tab).getPath(), GameData.scriptExtension)
                                       .equals(UtilsKt.baseFilename(map, GameData.mapExtension))) {
                                mainTabPane.getTabs().remove(tab); //TODO: Use closeTab(tab) to trigger onCloseRequest()?
                                break;
                            }
                        }

                        final MapEditTab mapEditTab = new MapEditTab(map);

                        mainTabPane.getTabs().add(mapEditTab);
                        mainTabPane.getSelectionModel().select(mapEditTab);
                        mainTabPane.requestFocus();
                    }
                    catch (final IOException | ParseException except) {
                        /*
                         * Do nothing - exception just signals that there was a map parsing
                         * or script reading issue and prevents us from adding the MapEditTab
                         * to the tab pane. A dialog was already shown to the user via the
                         * MapEditTab constructor.
                         */
                    }
                }
            }
        });
        contextMenuItems[MapListMenuItem.OPEN.ordinal()].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.OPEN.ordinal()]);

        contextMenuItems[MapListMenuItem.NEW.ordinal()]
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        //TODO: Create prompt for new mapname
        contextMenuItems[MapListMenuItem.NEW.ordinal()]
                .setOnAction(event -> FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                                                  Messages.INSTANCE.getString("KeroEdit.MapList.NEW")
                                                                                   .replace("_", ""),
                                                                  null, Messages.INSTANCE.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait());
        contextMenuItems[MapListMenuItem.NEW.ordinal()].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.NEW.ordinal()]);

        contextMenuItems[MapListMenuItem.DELETE.ordinal()].setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuItems[MapListMenuItem.DELETE.ordinal()].setOnAction(event -> {
            //This code works similarly to that in closeTabs() so reading that may help you to understand this

            /*
             * Reference new list to avoid list being modified because of deleted and deselected items
             * the List maintained by the ListView's SelectionModel doesn't properly adjust to insertions
             * and deletions and ends up being a pain to work with
             */
            //TODO: Avoid creating a new List if possible
            final List <Path> selectedMaps = new ArrayList <>(mapListView.getSelectionModel().getSelectedItems());
            for (int i = 0; i < selectedMaps.size() && 0 < selectedMaps.size(); ) {
                final Path map = selectedMaps.get(i);
                FXUtil.INSTANCE.createAlert(Alert.AlertType.CONFIRMATION, UtilsKt.baseFilename(map, GameData.mapExtension),
                                            null, Messages.INSTANCE.getString("KeroEdit.DeleteMap.MESSAGE")).showAndWait()
                               .ifPresent(result -> {
                                   if (ButtonType.OK == result) {
                                      /*
                                       * GameData.getMapList()is the backing list for the ListView,
                                       * so changes to the ListView's items affect GameData and vice versa.
                                       */
                                       mapListView.getItems().remove(map);
                                       selectedMaps.remove(map);

                                       //Close any MapEditTab or ScriptEditTab that bears the filename of this item
                                       for (final Tab tab : mainTabPane.getTabs()) {
                                           if ((tab instanceof MapEditTab && ((MapEditTab)tab).getPath().equals(map)) ||
                                               tab instanceof ScriptEditTab &&
                                               UtilsKt.baseFilename(((ScriptEditTab)tab).getPath(), GameData.scriptExtension)
                                                      .equals(UtilsKt.baseFilename(map, GameData.mapExtension))) {
                                              /*
                                               * Don't use close() in FXUtil as we've already confirmed
                                               * the user wants to delete the map, and thus that they
                                               * don't care about unsaved changes.
                                               */
                                               mainTabPane.getTabs().remove(tab);
                                               break;
                                           }
                                       }
                                   }
                               });

                //If user canceled map deletion, don't attempt to delete any others
                if (selectedMaps.contains(map)) {
                    return;
                }
            }

            mapListView.getSelectionModel().clearSelection();
        });
        contextMenuItems[MapListMenuItem.DELETE.ordinal()].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.DELETE.ordinal()]);

        //TODO: Create prompt for new mapname
        contextMenuItems[MapListMenuItem.RENAME.ordinal()]
                .setOnAction(event -> FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                                                  Messages.INSTANCE.getString("KeroEdit.MapList.RENAME")
                                                                                   .replace("_", ""),
                                                                  null, Messages.INSTANCE.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait());
        contextMenuItems[MapListMenuItem.RENAME.ordinal()].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.RENAME.ordinal()]);

        //TODO: Create prompt for new mapname
        contextMenuItems[MapListMenuItem.DUPLICATE.ordinal()]
                .setOnAction(event -> FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                                                  Messages.INSTANCE.getString("KeroEdit.MapList.DUPLICATE")
                                                                                   .replace("_", ""),
                                                                  null, Messages.INSTANCE.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait());
        contextMenuItems[MapListMenuItem.DUPLICATE.ordinal()].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.DUPLICATE.ordinal()]);

        mapListView.setOnKeyPressed(event -> {
            //This is the only one that doesn't seem to be triggered by keypresses on map list, IDK why
            final MenuItem openMap = contextMenuItems[MapListMenuItem.OPEN.ordinal()];
            if (openMap.getAccelerator().match(event)) {
                openMap.getOnAction().handle(new ActionEvent());
            }
        });

        mapListView.setOnMouseClicked(event -> {
            if (MouseButton.PRIMARY == event.getButton() && 2 == event.getClickCount()) {
                contextMenuItems[MapListMenuItem.OPEN.ordinal()].getOnAction().handle(new ActionEvent());
            }
        });

        return mapListView;
    }

    /**
     * Initializes the {@code TabPane} that forms the core of the KeroEdit program.
     *
     * @return The created {@code TabPane}
     */
    private TabPane initTabPane() {
        final TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        tabPane.getTabs().add(notepadTab = new NotepadTab());

        return tabPane;
    }

    /**
     * Closes tabs in {@code mainTabPane} such that if a given {@code Tab}
     * is an instance of {@code FileEditTab}, the tab's {@code onCloseRequest} property
     * will be invoked so that if the tab has unsaved changes, the user will be asked
     * if they want to save the changes, discard them, or cancel closing the tab.
     * If a single tab closing is canceled, the method quits immediately and returns true,
     * thereby not attempting to close any more tabs.
     *
     * @return true if a tab closing was canceled by the user, false otherwise.
     */
    private boolean closeTabs() {
        final ObservableList <Tab> tabs = mainTabPane.getTabs();
        for (int i = 0; i < tabs.size() && 0 < tabs.size(); ) {
            final Tab tab = tabs.get(i);
            if (notepadTab == tab) {
                i++;
                continue;
            }

            mainTabPane.getSelectionModel().select(tab);
            FXUtil.INSTANCE.close(tab);

            /*
             * If tab is a FileEditTab it won't actually close if the user presses
             * cancel on the unsaved changes prompt. So if they did cancel closing
             * a tab, quit immediately.
             */
            if (tabs.contains(tab)) {
                return true;
            }
        }

        return false;
    }

    /**
     * If the user has not yet read the license, this shows a prompt with
     * the license and asks if they agree to the license terms (Apache 2.0).
     * Also sets {@code Config.licenseRead}.
     */
    private void showLicense() {
        if (Config.INSTANCE.getLicenseRead()) {
            return;
        }

        final Path licensePath = ResourceManager.INSTANCE.getPath("LICENSE");

        if (null != licensePath) {
            try (Stream <String> lineStream = Files.lines(licensePath, Charset.forName("UTF-8"))) {
                final String licenseText = lineStream.collect(Collectors.joining("\n"));
                FXUtil.INSTANCE.createTextboxAlert(Alert.AlertType.CONFIRMATION,
                                                   Messages.INSTANCE.getString("KeroEdit.ReadLicense.TITLE"), null,
                                                   Messages.INSTANCE.getString("KeroEdit.ReadLicense.MESSAGE"),
                                                   licenseText, false).showAndWait()
                               .ifPresent(result -> {
                                   Config.INSTANCE.setLicenseRead(ButtonType.OK == result);
                                   if (!Config.INSTANCE.getLicenseRead()) {
                                       Config.INSTANCE.save();
                                       Platform.exit();
                                       System.exit(0);
                                   }
                               });
                return;
            }
            catch (final IOException except) {
                //jumps to FXUtil.INSTANCE.createAlert() below
            }
        }

        FXUtil.INSTANCE.createAlert(Alert.AlertType.INFORMATION,
                                    Messages.INSTANCE.getString("KeroEdit.ReadLicense.UnableToShow.TITLE"), null,
                                    Messages.INSTANCE.getString("KeroEdit.ReadLicense.UnableToShow.MESSAGE")).showAndWait();
        Config.INSTANCE.setLicenseRead(true); //allow program use, assuming they read it I guess
    }

    private static final class SettingsPane extends GridPane {
        private static final Font font = Font.font(null, FontWeight.BOLD, 15);

        SettingsPane() {
            setPadding(new Insets(10, 10, 10, 10));
            setVgap(10);
            setHgap(20);

            int x = 0;
            initDisplayedLayers(x++);
            initSelectedLayer(x++);
            initDrawModes(x++);
            initViewSettings(x++);
            initEditMode(x);
        }

        private void initDisplayedLayers(int x) {
            int y = 0;

            final Text label = new Text(Messages.INSTANCE.getString("KeroEdit.SettingsPane.DISPLAYED_LAYERS"));
            label.setFont(font);
            add(label, x, y++);

            final CheckBox[] cBoxes = {new CheckBox(Messages.INSTANCE.getString("PxPack.LayerNames.FOREGROUND")),
                                       new CheckBox(Messages.INSTANCE.getString("PxPack.LayerNames.MIDDLEGROUND")),
                                       new CheckBox(Messages.INSTANCE.getString("PxPack.LayerNames.BACKGROUND"))};

            for (int i = 0; i < cBoxes.length; ++i) {
                cBoxes[i].setAllowIndeterminate(false);
                //selected if the flag is set
                cBoxes[i].setSelected(Config.displayedLayers.contains(MapEditTab.Layer.values()[i]));

                final int layer = i;
                cBoxes[i].setOnAction(event -> {
                    final MapEditTab.Layer flag = MapEditTab.Layer.values()[layer];
                    if (cBoxes[layer].isSelected()) {
                        Config.displayedLayers.add(flag);
                    }
                    else {
                        Config.displayedLayers.remove(flag);
                    }
                    MapEditTab.setDisplayedLayers(Config.displayedLayers);
                });

                add(cBoxes[i], x, y++);
            }

            MapEditTab.setDisplayedLayers(Config.displayedLayers);
        }

        private void initSelectedLayer(int x) {
            int y = 0;

            final Text label = new Text(Messages.INSTANCE.getString("KeroEdit.SettingsPane.SELECTED_LAYER"));
            label.setFont(font);
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.INSTANCE.getString("PxPack.LayerNames.FOREGROUND")),
                                                new RadioButton(Messages.INSTANCE.getString("PxPack.LayerNames.MIDDLEGROUND")),
                                                new RadioButton(Messages.INSTANCE.getString("PxPack.LayerNames.BACKGROUND"))};

            radioButtons[Config.INSTANCE.getSelectedLayer().ordinal()].setSelected(true);

            for (int i = 0; i < radioButtons.length; ++i) {
                radioButtons[i].setToggleGroup(toggleGroup);

                final MapEditTab.Layer layer = MapEditTab.Layer.values()[i];
                radioButtons[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        MapEditTab.setSelectedLayer(layer);
                        Config.INSTANCE.setSelectedLayer(layer);
                    }
                });

                add(radioButtons[i], x, y++);
            }
        }

        private void initDrawModes(int x) {
            int y = 0;

            final Text label = new Text(Messages.INSTANCE.getString("KeroEdit.SettingsPane.DRAW_MODE"));
            label.setFont(font);
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.DRAW")),
                                                /*new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.RECT")),
                                                new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.COPY")),
                                                new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.FILL")),
                                                new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.REPLACE"))*/};

            radioButtons[Config.drawMode.ordinal()].setSelected(true);

            for (int i = 0; i < radioButtons.length; ++i) {
                radioButtons[i].setToggleGroup(toggleGroup);

                final int modeIndex = i;
                radioButtons[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    final MapEditTab.DrawMode mode = MapEditTab.DrawMode.values()[modeIndex];
                    MapEditTab.setDrawMode(mode);
                    Config.drawMode = mode;
                });

                add(radioButtons[i], x, y++);
            }
        }

        private void initViewSettings(int x) {
            int y = 0;

            final Text label = new Text(Messages.INSTANCE.getString("KeroEdit.SettingsPane.VIEW_SETTINGS"));
            label.setFont(font);
            add(label, x, y++);

            final CheckBox[] cBoxes = {new CheckBox(Messages.INSTANCE.getString("KeroEdit.SettingsPane.TILE_TYPES")),
                                       new CheckBox(Messages.INSTANCE.getString("KeroEdit.SettingsPane.GRID")),
                                       new CheckBox(Messages.INSTANCE.getString("KeroEdit.SettingsPane.ENTITY_BOXES")),
                                       new CheckBox(Messages.INSTANCE.getString("KeroEdit.SettingsPane.ENTITY_SPRITES")),
                                       /*new CheckBox(Messages.INSTANCE.getString("KeroEdit.SettingsPane.ENTITY_NAMES"))*/};

            for (int i = 0; i < cBoxes.length; ++i) {
                cBoxes[i].setAllowIndeterminate(false);

                cBoxes[i].setSelected(Config.viewSettings.contains(MapEditTab.ViewOption.values()[i]));

                final int index = i;
                cBoxes[i].setOnAction(event -> {
                    final MapEditTab.ViewOption flag = MapEditTab.ViewOption.values()[index];
                    if (cBoxes[index].isSelected()) {
                        Config.viewSettings.add(flag);
                    }
                    else {
                        Config.viewSettings.remove(flag);
                    }

                    MapEditTab.setViewSettings(Config.viewSettings);
                });

                add(cBoxes[i], x, y++);
            }

            MapEditTab.setViewSettings(Config.viewSettings);
        }

        private void initEditMode(int x) {
            int y = 0;

            final Text label = new Text(Messages.INSTANCE.getString("KeroEdit.SettingsPane.EDIT_MODE"));
            label.setFont(font);
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.TILE")),
                                                new RadioButton(Messages.INSTANCE.getString("KeroEdit.SettingsPane.ENTITY"))};

            radioButtons[Config.editMode.ordinal()].setSelected(true);

            for (int i = 0; i < radioButtons.length; ++i) {
                radioButtons[i].setToggleGroup(toggleGroup);

                final int modeIndex = i;
                radioButtons[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    final MapEditTab.EditMode mode = MapEditTab.EditMode.values()[modeIndex];
                    MapEditTab.setEditMode(mode);
                    Config.editMode = mode;
                });

                add(radioButtons[i], x, y++);
            }
        }
    }

    private static final class NotepadTab extends Tab {
        final TextArea notepad;

        NotepadTab() {
            super(Messages.INSTANCE.getString("KeroEdit.NOTEPAD_TITLE"));
            setId(Messages.INSTANCE.getString("KeroEdit.NOTEPAD_TITLE"));

            setClosable(false);

            setContent(notepad = new TextArea(Config.notepadText));
        }
    }

    private enum FileMenuItem implements SafeEnum <FileMenuItem> {
        OPEN,
        OPEN_LAST,
        SAVE,
        SAVE_ALL,
        CLOSE_TAB,
        CLOSE_ALL_TABS
    }

    private enum EditMenuItem implements SafeEnum <EditMenuItem> {
        UNDO,
        REDO
    }

    private enum ViewMenuItem implements SafeEnum <ViewMenuItem> {
        MAP_ZOOM,
        TILESET_ZOOM,
        TILESET_BG_COLOR
    }

    private enum ActionsMenuItem implements SafeEnum <ActionsMenuItem> {
        RUN_GAME,
        EDIT_GLOBAL_SCRIPT,
        HACK_EXECUTABLE,
        WAFFLE
    }

    private enum HelpMenuItem implements SafeEnum <HelpMenuItem> {
        ABOUT,
        GUIDE
    }

    private enum MapListMenuItem implements SafeEnum <MapListMenuItem> {
        OPEN,
        NEW,
        DELETE,
        DUPLICATE,
        RENAME //TODO: remove this?
    }
}