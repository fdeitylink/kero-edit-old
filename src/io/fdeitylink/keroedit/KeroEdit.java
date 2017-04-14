/*
 * TODO:
 * Barebones PNG editor
 *  - Color picker
 *  - Canvas
 *  - Reload tilesets in open maps on save (or on edit?)
 * Ctrl +/- and scroll wheel for zoom
 * Resort map ListView alphabetically when map is added, and select and open the new map
 * Draggable tabs (and allow popping out into a window)
 * Scaling map down
 * Lower memory usage and stuffs
 * Play pxtone files
 * Will need something for GameData changes to notify objects using it (i.e. maplist changes)
 *  - ObservableList?
 * In script editor, eventually put in an autocompleter for stuff like entity names
 * Add @throws to JavaDoc comments for runtime exceptions
 * Unify capitalization on mapname, mapName, mapNames
 * Use the n-dimensional array copy method?
 * Throw IllegalAccessExceptions in private constructors for classes not intended to be instantiated?
 */

package io.fdeitylink.keroedit;

import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import java.text.MessageFormat;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;
import java.text.ParseException;

import java.nio.file.DirectoryStream;

import java.nio.charset.Charset;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;

import javafx.stage.Stage;
import javafx.scene.control.Dialog;
import javafx.stage.Screen;
import javafx.scene.Scene;

import javafx.geometry.Rectangle2D;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import javafx.scene.control.ListView;
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

import javafx.beans.property.SimpleIntegerProperty;

import io.fdeitylink.keroedit.util.NullArgumentException;

import io.fdeitylink.keroedit.util.ArrayIndexEnum;

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.keroedit.util.FXUtil;

import io.fdeitylink.keroedit.gamedata.GameData;

import io.fdeitylink.keroedit.image.ImageManager;
import io.fdeitylink.keroedit.image.PxAttrManager;

import io.fdeitylink.keroedit.hack.HackTab;
import io.fdeitylink.keroedit.mapedit.MapEditTab;
import io.fdeitylink.keroedit.script.ScriptEditTab;

public final class KeroEdit extends Application {
    private ArrayList <MenuItem> enableOnLoadItems;

    private Stage mainStage;
    private TabPane mainTabPane;

    private MenuItem openLast;

    private NotepadTab notepadTab;

    private ListView <String> mapList;

    public static void main(final String[] args) {
        launch(args);
    }

    /**
     * Starts running the KeroEdit program and sets up its stage
     *
     * @param primaryStage The {@code Stage} to run the KeroEdit program in
     */
    @Override
    public void start(final Stage primaryStage) {
        final Thread.UncaughtExceptionHandler fxDefExceptHandler = Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            fxDefExceptHandler.uncaughtException(thread, throwable);
            Logger.logThrowable(throwable);
        });

        Config.loadPrefs();

        mainStage = primaryStage;
        mainStage.setOnCloseRequest(event -> {
            //if user didn't cancel any attempted tab closes and definitely wants to close program
            if (!closeTabs(true)) {
                Config.notepadText = notepadTab.notepad.getText();
                Config.savePrefs();

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
    }

    /**
     * Appends a given string to the base title of the program's {@code Stage}
     *
     * @param str The string to append to the title
     */
    public void setTitle(final String str) {
        mainStage.setTitle(MessageFormat.format(Messages.getString("KeroEdit.APP_TITLE"),
                                                Messages.getString("KeroEdit.VERSION")) + " - " + str);
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
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_TAB")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_ALL_TABS"))};
        fileMenu.getItems().addAll(menuItems);

        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.OPEN)].setOnAction(event -> {
            //if user didn't cancel any attempted tab closes and definitely wants to load new mod
            if (!closeTabs(true)) {
                wipeLoaded();

                final FileChooser exeChooser = new FileChooser();
                exeChooser.setTitle(Messages.getString("KeroEdit.OpenMod.TITLE"));

                final String initDir = Config.lastExeLoc.substring(0, Config.lastExeLoc.lastIndexOf(File.separatorChar) + 1);
                exeChooser.setInitialDirectory(new File(initDir));

                final FileChooser.ExtensionFilter extFilter =
                        new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.OpenMod.EXECUTABLE_FILTER"), "*.exe");
                exeChooser.getExtensionFilters().add(extFilter);

                final File exeFile = exeChooser.showOpenDialog(mainStage);
                if (null != exeFile) {
                    loadMod(exeFile.toPath());
                }
            }
        });

        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.OPEN_LAST)]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.OPEN_LAST)].setOnAction(event -> {
            //if user didn't cancel any attempted tab closes and definitely wants to load last mod
            if (!closeTabs(true)) {
                wipeLoaded();
                loadMod(Paths.get(Config.lastExeLoc));
            }
        });
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.OPEN_LAST)]
                .setDisable(!Config.lastExeLoc.endsWith(".exe")); //Disabled if there is no last mod
        openLast = menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.OPEN_LAST)];

        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE)].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FXUtil.FileEditTab) {
                ((FXUtil.FileEditTab)selectedTab).save();
            }
        });
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE)].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE)]);

        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE_ALL)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE_ALL)].setOnAction(event -> {
            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof FXUtil.FileEditTab) {
                    ((FXUtil.FileEditTab)tab).save();
                }
            }
        });
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE_ALL)].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.SAVE_ALL)]);

        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_TAB)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_TAB)].setOnAction(event -> {
            final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            if (-1 != tabIndex && mainTabPane.getTabs().get(tabIndex) != notepadTab) {
                FXUtil.closeTab(mainTabPane.getTabs().get(tabIndex));
            }
        });
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_TAB)].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_TAB)]);

        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_ALL_TABS)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_ALL_TABS)].setOnAction(event -> closeTabs(false));
        menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_ALL_TABS)].setDisable(true);
        enableOnLoadItems.add(menuItems[FileMenuItem.arrayIndexMap.get(FileMenuItem.CLOSE_ALL_TABS)]);

        return fileMenu;
    }

    /**
     * Erases all stored and open remnants of the currently loaded mod.
     * By now the user should have saved or discarded their work and
     * confirmed they want to load a new mod.
     */
    private void wipeLoaded() {
        setTitle("");

        mapList.getItems().clear();

        for (final MenuItem mItem : enableOnLoadItems) {
            if (!mItem.isDisable()) {
                mItem.setDisable(true);
            }
        }

        GameData.wipe();
        HackTab.wipe();
        ImageManager.wipe();
        PxAttrManager.wipe();
        MapEditTab.wipeImages();
    }

    /**
     * Loads a mod, checking if it is valid. Also creates its assist folder.
     *
     * @param executable A {@code Path} that references the executable for a mod
     *
     * @throws NullArgumentException if {@code executable} is null
     */
    private void loadMod(final Path executable) {
        try {
            new Task <Void>() {
                @Override
                protected Void call() throws Exception {
                    if (null == executable) {
                        throw new NullArgumentException("loadMod", "executable");
                    }
                    if (!executable.toString().endsWith(".exe")) {
                        Platform.runLater(() -> FXUtil.createAlert(Alert.AlertType.ERROR,
                                                                   Messages.getString("KeroEdit.LoadMod.NotExe.TITLE"), null,
                                                                   MessageFormat.format(Messages.getString("KeroEdit.LoadMod.NotExe.MESSAGE"),
                                                                                        executable.toAbsolutePath())).showAndWait());
                        return null;
                    }

                    try {
                        GameData.init(executable);
                        Config.lastExeLoc = executable.toAbsolutePath().toString();

                        HackTab.init();

                        final File executableParent = executable.getParent().toAbsolutePath().toFile();
                        boolean hasRWXPermissions = executableParent.canRead() && executableParent.canWrite() &&
                                                    executableParent.canExecute();
                        if (!hasRWXPermissions) {
                            if (!(hasRWXPermissions = executableParent.setReadable(true) &&
                                                      executableParent.setWritable(true) &&
                                                      executableParent.setExecutable(true))) {
                                Platform.runLater(() -> FXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                                           Messages.getString("KeroEdit.LoadMod.StrictPermissions.TITLE"),
                                                                           null,
                                                                           Messages.getString("KeroEdit.LoadMod.StrictPermissions.MESSAGE"))
                                                              .showAndWait());
                            }
                        }
                        if (hasRWXPermissions) {
                            createAssistFolder();
                        }

                        Platform.runLater(() -> {
                            setTitle(executable.getParent().toAbsolutePath().toString() + File.separatorChar);

                            openLast.setDisable(false);
                            mapList.setItems(FXCollections.observableArrayList(GameData.getMapList()));
                            mapList.requestFocus();

                            for (final MenuItem mItem : enableOnLoadItems) {
                                if (mItem.isDisable()) {
                                    mItem.setDisable(false);
                                }
                            }
                        });
                    }
                    catch (final IOException except) {
                        Platform.runLater(() -> FXUtil.createAlert(Alert.AlertType.ERROR,
                                                                   Messages.getString("KeroEdit.LoadMod.IOExcept.TITLE"), null,
                                                                   except.getMessage()).showAndWait());
                    }

                    return null;
                }
            }.call();
        }
        catch (final Exception except) {
            //do nothing?
        }
    }

    /**
     * Creates the assist folder for a mod inside of its resource folder
     */
    private void createAssistFolder() {
        try {
            final Path modAssistPath = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                 File.separatorChar + "assist");
            if (!Files.exists(modAssistPath)) {
                Files.createDirectory(modAssistPath);
            }

            final Path internalAssistPath = ResourceManager.getPath("assist");
            if (null == internalAssistPath) {
                throw new IOException(); //jumps to outer catch block with CopyFolderFail
            }

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

            final DirectoryStream <Path> assistPaths = Files.newDirectoryStream(internalAssistPath);
            for (final Path p : assistPaths) {
                //skip if wrong *_strings.json file
                if (p.toString().endsWith("_strings.json") && !p.getFileName().toString().equals(stringsFname)) {
                    continue;
                }

                try {
                    final Path modCopyPath = Paths.get(modAssistPath.toString() + File.separatorChar + p.getFileName());
                    if (!Files.exists(modCopyPath)) {
                        Files.copy(p, modCopyPath);
                    }
                }
                catch (final IOException except) {
                    FXUtil.createAlert(Alert.AlertType.ERROR,
                                       Messages.getString("KeroEdit.CreateAssist.CopyFileFail.TITLE"), null,
                                       MessageFormat.format(Messages.getString("KeroEdit.CreateAssist.CopyFileFail.MESSAGE"),
                                                            p.getFileName())).showAndWait();
                    //TODO: skip the rest of the files?
                }
            }
        }
        catch (final IOException except) {
            FXUtil.createAlert(Alert.AlertType.ERROR,
                               Messages.getString("KeroEdit.CreateAssist.CopyFolderFail.TITLE"), null,
                               Messages.getString("KeroEdit.CreateAssist.CopyFolderFail.MESSAGE")).showAndWait();
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

        menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.UNDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.UNDO)].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FXUtil.FileEditTab) {
                ((FXUtil.FileEditTab)selectedTab).undo();
            }
        });
        menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.UNDO)]
                .setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.UNDO)]);

        menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.REDO)]
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.REDO)].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FXUtil.FileEditTab) {
                ((FXUtil.FileEditTab)selectedTab).redo();
            }
        });
        menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.REDO)]
                .setDisable(true); //Disable until valid mod opened
        enableOnLoadItems.add(menuItems[EditMenuItem.arrayIndexMap.get(EditMenuItem.REDO)]);

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

        final RadioMenuItem[] mapZoomMenuItems = buildZoomSubmenu(Config.mapZoom);
        int zoom = 2;
        for (final RadioMenuItem mapZoomMItem : mapZoomMenuItems) {
            final int z = zoom;
            mapZoomMItem.setOnAction(event -> MapEditTab.setMapZoom(Config.mapZoom = z));
            zoom += 2;
        }
        ((Menu)menuItems[ViewMenuItem.arrayIndexMap.get(ViewMenuItem.MAP_ZOOM)]).getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = buildZoomSubmenu(Config.tilesetZoom);
        zoom = 2;
        for (final RadioMenuItem tilesetZoomMItem : tilesetZoomMenuItems) {
            final int z = zoom;
            tilesetZoomMItem.setOnAction(event -> MapEditTab.setTilesetZoom(Config.tilesetZoom = z));
            zoom += 2;
        }
        ((Menu)menuItems[ViewMenuItem.arrayIndexMap.get(ViewMenuItem.TILESET_ZOOM)])
                .getItems().addAll(tilesetZoomMenuItems);

        menuItems[ViewMenuItem.arrayIndexMap.get(ViewMenuItem.TILESET_BG_COLOR)].setOnAction(event -> {
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
     * will be selected.
     *
     * @return An array of {@code RadioMenuItem}s in a {@code ToggleGroup} that can be used
     * to set a zoom level. None of them are bound to an {@code EventHandler <ActionEvent>},
     * so that must be done after they are created
     */
    private RadioMenuItem[] buildZoomSubmenu(final int defaultZoom) {
        final ToggleGroup zoomToggleGroup = new ToggleGroup();
        final RadioMenuItem[] zoomMenuItems = new RadioMenuItem[3];

        //from 200, 400, 600% since attribute.png and unittype.png are are 16px by 16px but tiles are 8px by 8px
        //and I don't have a half-scale image scaler yet
        int zoom = 2;
        for (int i = 0; i < zoomMenuItems.length; ++i) {
            zoomMenuItems[i] = new RadioMenuItem(String.valueOf(zoom * 100) + '%');
            zoomMenuItems[i].setToggleGroup(zoomToggleGroup);
            zoomMenuItems[i].setSelected(defaultZoom == zoom);

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

        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.RUN_GAME)].setOnAction(event -> {
            final Runtime run = Runtime.getRuntime();
            try {
                run.exec(GameData.getExecutable().toAbsolutePath().toString());
            }
            catch (final IOException except) {
                FXUtil.createAlert(Alert.AlertType.ERROR,
                                   Messages.getString("KeroEdit.RunGame.IOExcept.TITLE"), null,
                                   Messages.getString("KeroEdit.RunGame.IOExcept.MESSAGE"));
            }
        });
        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.RUN_GAME)].setDisable(true);
        enableOnLoadItems.add(menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.RUN_GAME)]);

        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.EDIT_GLOBAL_SCRIPT)].setOnAction(event -> {
            final FileChooser scrChooser = new FileChooser();
            scrChooser.setTitle(Messages.getString("KeroEdit.GlobalScript.TITLE"));

            scrChooser.setInitialDirectory(new File(GameData.getResourceFolder().toAbsolutePath().toString()));

            final FileChooser.ExtensionFilter[] extFilters =
                    {new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.GlobalScript.SCRIPT_FILTER"), "*.pxeve"),
                     new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.GlobalScript.NO_FILTER"), "*.*")};
            scrChooser.getExtensionFilters().addAll(extFilters);
            scrChooser.setSelectedExtensionFilter(extFilters[0]);

            final List <File> scriptFiles = scrChooser.showOpenMultipleDialog(mainStage);
            if (null != scriptFiles) {
                for (final File f : scriptFiles) {
                    final Path scriptPath = f.toPath();

                    boolean isAlreadyOpen = false;
                    for (final Tab tab : mainTabPane.getTabs()) {
                        if (tab instanceof ScriptEditTab &&
                            tab.getId().equals(scriptPath.toAbsolutePath().toString())) {
                            mainTabPane.getSelectionModel().select(tab);
                            mainTabPane.requestFocus();
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
                         * Do nothing - the exception just signals that there was a script reading issue
                         * and prevents us from adding the ScriptEditTab to the tab pane.
                         * A dialog was already shown to the user via the ScriptEditTab constructor.
                         */
                        }
                    }
                }
            }
        });
        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.EDIT_GLOBAL_SCRIPT)].setDisable(true);
        enableOnLoadItems.add(menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.EDIT_GLOBAL_SCRIPT)]);

        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.HACK_EXECUTABLE)].setOnAction(event -> {
            /*for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof HackTab) {
                    mainTabPane.getSelectionModel().select(tab);
                    mainTabPane.requestFocus();
                    return;
                }
            }*/
            if (!mainTabPane.getTabs().contains(HackTab.getInst())) {
                mainTabPane.getTabs().add(HackTab.getInst());
            }

            mainTabPane.getSelectionModel().select(HackTab.getInst());
            mainTabPane.requestFocus();

        });
        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.HACK_EXECUTABLE)].setDisable(true);
        enableOnLoadItems.add(menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.HACK_EXECUTABLE)]);

        menuItems[ActionsMenuItem.arrayIndexMap.get(ActionsMenuItem.WAFFLE)].setOnAction(event -> {
            final Image waffleImg = FXUtil.scaleImage(ResourceManager.getImage("waffle.png"), 16);
            final PrinterJob printJob = PrinterJob.createPrinterJob();
            if (null != printJob) {
                if (printJob.showPrintDialog(mainStage)) {
                    final boolean success = printJob.printPage(new ImageView(waffleImg));
                    if (success) {
                        printJob.endJob();
                    }
                    else {
                        FXUtil.createAlert(Alert.AlertType.ERROR,
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


        menuItems[HelpMenuItem.arrayIndexMap.get(HelpMenuItem.ABOUT)].setOnAction(event -> {
            final Alert aboutAlert = FXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                        Messages.getString("KeroEdit.HelpMenu.About.TITLE"), null,
                                                        MessageFormat.format(Messages.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                             Messages.getString("KeroEdit.LAST_UPDATE"),
                                                                             Messages.getString("KeroEdit.VERSION")));

            aboutAlert.getDialogPane().setGraphic(new ImageView(ResourceManager.getImage("fdl_logo.png")));
            aboutAlert.showAndWait();
        });

        menuItems[HelpMenuItem.arrayIndexMap.get(HelpMenuItem.GUIDE)]
                .setOnAction(event -> FXUtil.createAlert(Alert.AlertType.INFORMATION,
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
    private ListView <String> initMapList() {
        final ListView <String> mapListView = new ListView <>();
        mapListView.setOrientation(Orientation.VERTICAL);

        mapListView.setMinWidth(125);
        mapListView.setPrefWidth(125);

        mapListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        final MenuItem[] contextMenuItems = {new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.OPEN_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DELETE_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP"))};

        final ContextMenu contextMenu = new ContextMenu(contextMenuItems);
        mapListView.setContextMenu(contextMenu);

        //TODO: Change accelerator text from an arrow to "Enter"
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.OPEN)].setOnAction(event -> {
            for (final String mapName : mapListView.getSelectionModel().getSelectedItems()) {
                boolean isAlreadyOpen = false;
                for (final Tab tab : mainTabPane.getTabs()) {
                    if (tab instanceof MapEditTab && tab.getId().equals(mapName)) {
                        mainTabPane.getSelectionModel().select(tab);
                        mainTabPane.requestFocus();
                        isAlreadyOpen = true;
                        break;
                    }
                }

                if (!isAlreadyOpen) {
                    try {
                        final MapEditTab mapEditTab = new MapEditTab(mapName);
                        mainTabPane.getTabs().add(mapEditTab);
                        mainTabPane.getSelectionModel().select(mapEditTab);
                        mainTabPane.requestFocus();
                    }
                    catch (final IOException | ParseException except) {
                        //do nothing - the exception just signals that there was a map parsing or script reading issue
                        //dialog box already shown via the MapEditTab constructor
                    }
                }
            }
        });
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.OPEN)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.OPEN)]);

        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.NEW)]
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        //TODO: Create prompt for new mapname
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.NEW)]
                .setOnAction(event -> FXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                         Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")
                                                                 .replace("_", ""),
                                                         null, Messages.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait());
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.NEW)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.NEW)]);

        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DELETE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DELETE)].setOnAction(event -> {
            //This code works similarly to that in the closeTabs() method so reading that may help you to understand this

            //reference completely new list to avoid list being modified because of deleted and deselected items
            final ArrayList <String> selectedMapNames =
                    new ArrayList <>(mapListView.getSelectionModel().getSelectedItems());
            for (int i = 0; i < selectedMapNames.size() && 0 < selectedMapNames.size(); ) {
                final String mapName = selectedMapNames.get(i);

                FXUtil.createAlert(Alert.AlertType.CONFIRMATION, mapName, null,
                                   Messages.getString("KeroEdit.DeleteMap.MESSAGE")).showAndWait()
                      .ifPresent(result -> {
                          if (ButtonType.OK == result) {
                              GameData.removeMap(mapName);
                              mapListView.getItems().remove(mapName);
                              selectedMapNames.remove(mapName);

                              for (final Tab tab : mainTabPane.getTabs()) {
                                  if (tab instanceof MapEditTab && tab.getId().equals(mapName)) {
                                      /*
                                       * Don't use FXUtil.closeTab() as we've already confirmed
                                       * the user wants to delete the map, and thus doesn't care
                                       * about unsaved changes.
                                       */
                                      mainTabPane.getTabs().remove(tab);
                                      break;
                                  }
                              }
                          }
                      });
                /*
                 * If user canceled map deletion, i++ moves onto the next
                 * index where the following selected map name will be.
                 * If map was deleted, then i, which used to point to the
                 * map name that was just removed, will now point to the next
                 * selected map name (or none if we've iterated through
                 * every selected map name).
                 */
                if (selectedMapNames.contains(mapName)) {
                    i++;
                }
            }

            mapListView.getSelectionModel().clearSelection();
        });
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DELETE)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DELETE)]);

        //TODO: Create prompt for new mapname
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.RENAME)]
                .setOnAction(event -> FXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                         Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")
                                                                 .replace("_", ""),
                                                         null, Messages.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait());
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.RENAME)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.RENAME)]);

        //TODO: Create prompt for new mapname
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DUPLICATE)]
                .setOnAction(event -> FXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                         Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP")
                                                                 .replace("_", ""),
                                                         null, Messages.getString("KeroEdit.NOT_IMPLEMENTED")).showAndWait());
        contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DUPLICATE)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.DUPLICATE)]);

        mapListView.setOnKeyPressed(event -> {
            /*
             * This code is from before when it seemed that the MenuItems
             * in the context menu weren't being triggered by keypresses
             * on the map list itself. Now it seems they get triggered so
             * I don't think this is necessary anymore, but it's being kept
             * in case it is needed for whatever reason.
             */
            //Retrieves MenuItem in the context menu that the key is bound to
            /*MenuItem menuItem = null;
            for (final MenuItem mItem : contextMenuItems) {
                final KeyCombination keyCombo = mItem.getAccelerator();
                if (null != keyCombo && keyCombo.match(event)) {
                    menuItem = mItem;
                    break;
                }
            }
            if (null != menuItem) {
                menuItem.getOnAction().handle(new ActionEvent());
            }*/

            //This is the only one that doesn't seem to be triggered by keypresses on map list, IDK why
            final MenuItem openMap = contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.OPEN)];
            if (openMap.getAccelerator().match(event)) {
                openMap.getOnAction().handle(new ActionEvent());
            }
        });

        mapListView.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()) {
                contextMenuItems[MapListMenuItem.arrayIndexMap.get(MapListMenuItem.OPEN)].getOnAction().handle(new ActionEvent());
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
        tabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);

        tabPane.getTabs().add(notepadTab = new NotepadTab());

        return tabPane;
    }

    /**
     * Closes tabs in {@code mainTabPane} such that if a given {@code Tab}
     * is an instance of {@code FileEditTab}, the tab's {@code onCloseRequest} property
     * will be invoked so that if the tab has unsaved changes, the user will be asked
     * if they want to save the changes, discard them, or cancel closing the tab.
     *
     * @param cancelAll True if the first time a user hits cancel on a tab close dialog
     * should cancel all additional attempts to close a tab and return true, or false if
     * all tab closes are completely independent of each other and canceling one tab closing
     * should not cancel attempts to close others.
     *
     * @return true if {@code cancelAll} is true and at least one tab closing was canceled by the user.
     */
    private boolean closeTabs(final boolean cancelAll) {
        final ObservableList <Tab> tabs = mainTabPane.getTabs();
        for (int i = 0; i < tabs.size() && 0 < tabs.size(); ) {
            final Tab tab = tabs.get(i);
            if (notepadTab == tab) {
                i++;
                continue;
            }

            mainTabPane.getSelectionModel().select(tab);
            FXUtil.closeTab(tab);

            /*
             * If tab is a FileEditTab it won't actually close if the user presses
             * cancel on the unsaved changes prompt. So the i++ moves onto the next
             * index where the following tab will be. If the tab did close, then i,
             * which used to point to the tab that was just closed, will now point
             * to the next tab (or none if we've iterated through every tab). This
             * is also how the i++ in the if-statement above works.
             */
            if (tabs.contains(tab)) {
                if (cancelAll) {
                    return true;
                }
                i++;
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
        if (Config.licenseRead) {
            return;
        }

        final Path licensePath = ResourceManager.getPath("LICENSE");

        if (null != licensePath) {
            try (Stream <String> lineStream = Files.lines(licensePath, Charset.forName("UTF-8"))) {
                final String licenseText = lineStream.collect(Collectors.joining("\n"));
                FXUtil.createTextboxAlert(Alert.AlertType.CONFIRMATION,
                                          Messages.getString("KeroEdit.ReadLicense.TITLE"), null,
                                          Messages.getString("KeroEdit.ReadLicense.MESSAGE"),
                                          licenseText, false).showAndWait()
                      .ifPresent(result -> {
                          Config.licenseRead = ButtonType.OK == result;
                          if (!Config.licenseRead) {
                              Platform.exit();
                              System.exit(0);
                          }
                      });
                return;
            }
            catch (final IOException except) {
                //jumps to FXUtil.createAlert() below
            }
        }

        FXUtil.createAlert(Alert.AlertType.INFORMATION,
                           Messages.getString("KeroEdit.ReadLicense.UnableToShow.TITLE"), null,
                           Messages.getString("KeroEdit.ReadLicense.UnableToShow.MESSAGE")).showAndWait();
        Config.licenseRead = true; //allow program use, assuming they read it I guess
    }

    private static final class SettingsPane extends GridPane {
        SettingsPane() {
            setPadding(new Insets(10, 10, 10, 10));
            setVgap(10);
            setHgap(20);

            int x = 0;
            initDisplayedLayers(x++, 0);
            initSelectedLayer(x++, 0);
            initDrawModes(x++, 0);
            initViewSettings(x, 0);
        }

        private void initDisplayedLayers(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.DISPLAYED_LAYERS"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final SimpleIntegerProperty displayedLayers = new SimpleIntegerProperty(Config.displayedLayers);

            final CheckBox[] cBoxes = {new CheckBox(Messages.getString("PxPack.LayerNames.FOREGROUND")),
                                       new CheckBox(Messages.getString("PxPack.LayerNames.MIDDLEGROUND")),
                                       new CheckBox(Messages.getString("PxPack.LayerNames.BACKGROUND"))};

            for (int i = 0; i < cBoxes.length; ++i) {
                cBoxes[i].setAllowIndeterminate(false);
                //selected if the flag is set
                cBoxes[i].setSelected(MapEditTab.LayerFlag.values()[i].flag ==
                                      (Config.displayedLayers & MapEditTab.LayerFlag.values()[i].flag));

                final int layer = i;
                cBoxes[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    int newDispLayersFlag = displayedLayers.get();
                    if (newValue) {
                        //enable flag
                        newDispLayersFlag |= MapEditTab.LayerFlag.values()[layer].flag;
                    }
                    else {
                        //disable flag
                        newDispLayersFlag &= MapEditTab.LayerFlag.values()[layer].flag ^ 0b1111_1111;
                    }

                    displayedLayers.set(newDispLayersFlag);
                    Config.displayedLayers = newDispLayersFlag;
                });

                add(cBoxes[i], x, y++);
            }

            MapEditTab.bindDisplayedLayers(displayedLayers);
        }

        private void initSelectedLayer(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.SELECTED_LAYER"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.getString("PxPack.LayerNames.FOREGROUND")),
                                                new RadioButton(Messages.getString("PxPack.LayerNames.MIDDLEGROUND")),
                                                new RadioButton(Messages.getString("PxPack.LayerNames.BACKGROUND"))};

            radioButtons[Config.selectedLayer].setSelected(true);

            for (int i = 0; i < radioButtons.length; ++i) {
                radioButtons[i].setToggleGroup(toggleGroup);

                final int layer = i;
                radioButtons[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        MapEditTab.setSelectedLayer(layer);
                        Config.selectedLayer = layer;
                    }
                });

                add(radioButtons[i], x, y++);
            }
        }

        private void initDrawModes(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.DRAW_MODE"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.getString("KeroEdit.SettingsPane.DRAW"))/*,
                                                new RadioButton(Messages.getString("KeroEdit.SettingsPane.RECT")),
                                                new RadioButton(Messages.getString("KeroEdit.SettingsPane.COPY")),
                                                new RadioButton(Messages.getString("KeroEdit.SettingsPane.FILL")),
                                                new RadioButton(Messages.getString("KeroEdit.SettingsPane.REPLACE"))*/};

            radioButtons[MapEditTab.DrawMode.arrIndexEnumMap.get(Config.drawMode)].setSelected(true);

            for (int i = 0; i < radioButtons.length; ++i) {
                final int modeIndex = i;
                radioButtons[i].setOnAction(event -> {
                    final MapEditTab.DrawMode mode = MapEditTab.DrawMode.values()[modeIndex];
                    MapEditTab.setDrawMode(mode);
                    Config.drawMode = mode;
                });
                radioButtons[i].setToggleGroup(toggleGroup);
                add(radioButtons[i], x, y++);
            }
        }

        private void initViewSettings(int x, int y) {
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.VIEW_SETTINGS"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final SimpleIntegerProperty viewSettings = new SimpleIntegerProperty(Config.viewSettings);

            final CheckBox[] cBoxes = {new CheckBox(Messages.getString("KeroEdit.SettingsPane.TILE_TYPES")),
                                       new CheckBox(Messages.getString("KeroEdit.SettingsPane.GRID")),
                                       /*new CheckBox(Messages.getString("KeroEdit.SettingsPane.ENTITY_BOXES")),
                                       new CheckBox(Messages.getString("KeroEdit.SettingsPane.ENTITY_SPRITES")),
                                       new CheckBox(Messages.getString("KeroEdit.SettingsPane.ENTITY_NAMES"))*/};

            for (int i = 0; i < cBoxes.length; ++i) {
                cBoxes[i].setAllowIndeterminate(false);

                cBoxes[i].setSelected(MapEditTab.ViewFlag.values()[i].flag ==
                                      (Config.viewSettings & MapEditTab.ViewFlag.values()[i].flag));

                final int index = i;
                cBoxes[i].selectedProperty().addListener((observable, oldValue, newValue) -> {
                    int newViewSettingsFlag = viewSettings.get();
                    if (newValue) {
                        //enable flag
                        newViewSettingsFlag |= MapEditTab.ViewFlag.values()[index].flag;
                    }
                    else {
                        //disable flag
                        newViewSettingsFlag &= MapEditTab.ViewFlag.values()[index].flag ^ 0b1111_1111;
                    }

                    viewSettings.set(newViewSettingsFlag);
                    Config.viewSettings = newViewSettingsFlag;
                });

                add(cBoxes[i], x, y++);
            }

            MapEditTab.bindViewSettings(viewSettings);
        }
    }

    private static final class NotepadTab extends Tab {
        private final TextArea notepad;

        NotepadTab() {
            super(Messages.getString("KeroEdit.NOTEPAD_TITLE"));
            setId(Messages.getString("KeroEdit.NOTEPAD_TITLE"));

            setClosable(false);

            setContent(notepad = new TextArea(Config.notepadText));
        }
    }

    private enum FileMenuItem implements ArrayIndexEnum <FileMenuItem> {
        OPEN,
        OPEN_LAST,
        SAVE,
        SAVE_ALL,
        CLOSE_TAB,
        CLOSE_ALL_TABS;

        static final EnumMap <FileMenuItem, Integer> arrayIndexMap = OPEN.enumMap(FileMenuItem.class);
    }

    private enum EditMenuItem implements ArrayIndexEnum <EditMenuItem> {
        UNDO,
        REDO;

        static final EnumMap <EditMenuItem, Integer> arrayIndexMap = UNDO.enumMap(EditMenuItem.class);
    }

    private enum ViewMenuItem implements ArrayIndexEnum <ViewMenuItem> {
        MAP_ZOOM,
        TILESET_ZOOM,
        TILESET_BG_COLOR;

        static final EnumMap <ViewMenuItem, Integer> arrayIndexMap = MAP_ZOOM.enumMap(ViewMenuItem.class);
    }

    private enum ActionsMenuItem implements ArrayIndexEnum <ActionsMenuItem> {
        RUN_GAME,
        EDIT_GLOBAL_SCRIPT,
        HACK_EXECUTABLE,
        WAFFLE;

        static final EnumMap <ActionsMenuItem, Integer> arrayIndexMap = RUN_GAME.enumMap(ActionsMenuItem.class);
    }

    private enum HelpMenuItem implements ArrayIndexEnum <HelpMenuItem> {
        ABOUT,
        GUIDE;

        static final EnumMap <HelpMenuItem, Integer> arrayIndexMap = ABOUT.enumMap(HelpMenuItem.class);
    }

    private enum MapListMenuItem implements ArrayIndexEnum <MapListMenuItem> {
        OPEN,
        NEW,
        DELETE,
        DUPLICATE,
        RENAME; //TODO: remove this?

        static final EnumMap <MapListMenuItem, Integer> arrayIndexMap = OPEN.enumMap(MapListMenuItem.class);
    }
}