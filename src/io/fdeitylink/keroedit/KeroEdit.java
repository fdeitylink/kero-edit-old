/*
 * TODO:
 * Barebones png editor (color picker and canvas) and reload tilesets in open maps on save (or on edit?)
 * Ctrl +/- and scrollwheel for zoom
 * Undoable map delete?
 * Put enum filler into method in util package
 * Use Tab.setOnCloseRequest() to warn about unsaved changes
 * Resort the map ListView alphabetically when a map is added, and select and open the new map
 * Draggable tabs (and allow popping out into a window)
 * Allow opening multiple maps at once (multiple selection)
 * Allow configuring tile size?
 * Investigate tab-related exceptions, slowness, and NoClassDefFoundError exception when trying to save prefs
 * Log runtime/uncaught exceptions
 * Scaling map down
 * Lower memory usage and stuffs
 * Allow changing tilesets in map edit tab (so it will have to change head properties)
 * Play pxtone files
 * Will need something for GameData changes to notify objects using it (i.e. maplist changes)
 * In script editor, eventually put in an autocompleter for stuff like entity names
 */

package io.fdeitylink.keroedit;

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

import java.nio.file.DirectoryStream;

import java.nio.charset.Charset;

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

import javafx.beans.property.SimpleIntegerProperty;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.keroedit.util.JavaFXUtil;

import io.fdeitylink.keroedit.util.FileEditTab;

import io.fdeitylink.keroedit.map.PxPack;

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

    private NotepadTab notepadTab;

    private ListView <String> mapList;

    public static void main(final String[] args) {
        launch(args);
    }

    /**
     * Starts running the KeroEdit program and sets up its stage
     *
     * @param stage The stage to run the KeroEdit program in
     */
    @Override
    public void start(final Stage stage) {
        Config.loadPreferences();

        mainStage = stage;
        mainStage.setOnCloseRequest(event -> {
            Config.notepadText = notepadTab.notepad.getText();
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
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.RELOAD")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_TAB")),
                                      new MenuItem(Messages.getString("KeroEdit.FileMenu.CLOSE_ALL_TABS"))};
        fileMenu.getItems().addAll(menuItems);

        final EnumMap <FileMenuItems, Integer> fileMenuItems = new EnumMap <>(FileMenuItems.class);

        {
            int i = 0;
            for (final FileMenuItems x : FileMenuItems.values()) {
                fileMenuItems.put(x, i++);
            }
        }

        menuItems[fileMenuItems.get(FileMenuItems.OPEN)]
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.OPEN)].setOnAction(event -> {
            wipeLoaded();

            final FileChooser exeChooser = new FileChooser();
            exeChooser.setTitle(Messages.getString("KeroEdit.OpenFile.TITLE"));

            final String initDir = Config.lastExeLoc.substring(0, Config.lastExeLoc.lastIndexOf(File.separatorChar) + 1);
            exeChooser.setInitialDirectory(new File(initDir));

            final FileChooser.ExtensionFilter[] extFilters =
                    {new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.OpenFile.EXECUTABLE_FILTER"), "*.exe"),
                     new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.OpenFile.NO_FILTER"), "*.*")};
            exeChooser.getExtensionFilters().addAll(extFilters);
            exeChooser.setSelectedExtensionFilter(extFilters[0]);

            final File exeFile = exeChooser.showOpenDialog(mainStage);

            if (null != exeFile) {
                try {
                    loadMod(exeFile.toPath());
                }
                catch (final IOException except) {
                    JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("KeroEdit.LoadMod.Except.TITLE"),
                                           null, except.getMessage()).showAndWait();
                }
            }
        });

        menuItems[fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.OPEN_LAST)].setOnAction(event -> {
            wipeLoaded();

            //TODO: Put into Task
            try {
                loadMod(Paths.get(Config.lastExeLoc));
            }
            catch (final IOException except) {
                JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("KeroEdit.LoadMod.Except.TITLE"), null,
                                       except.getMessage()).showAndWait();
            }
        });

        menuItems[fileMenuItems.get(FileMenuItems.OPEN_LAST)]
                .setDisable(Config.lastExeLoc.equals(System.getProperty("user.dir")) ||
                            !Config.lastExeLoc.endsWith(".exe")); //Disabled if there is no last mod

        menuItems[fileMenuItems.get(FileMenuItems.SAVE)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.SAVE)].setOnAction(event -> {
            final Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab instanceof FileEditTab) {
                ((FileEditTab)selectedTab).save();
            }
        });
        menuItems[fileMenuItems.get(FileMenuItems.SAVE)].setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.SAVE)]);

        menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)]
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)].setOnAction(event -> {
            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof FileEditTab) {
                    ((FileEditTab)tab).save();
                }
            }
        });
        menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)].setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.SAVE_ALL)]);

        menuItems[fileMenuItems.get(FileMenuItems.RELOAD)]
                .setAccelerator(new KeyCodeCombination(KeyCode.F5));
        menuItems[fileMenuItems.get(FileMenuItems.RELOAD)].setOnAction(event -> {
            wipeLoaded();

            //TODO: Warn about unsaved changes
            try {
                loadMod(GameData.getExecutable());
            }
            catch (final IOException except) {
                JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("KeroEdit.LoadMod.Except.TITLE"), null,
                                       except.getMessage()).showAndWait();
            }
        });
        menuItems[fileMenuItems.get(FileMenuItems.RELOAD)].setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.RELOAD)]);

        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)].setOnAction(event -> {
            final int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            if (-1 != tabIndex && mainTabPane.getTabs().get(tabIndex) != notepadTab) {
                mainTabPane.getTabs().remove(tabIndex);
            }
        });
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)].setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.CLOSE_TAB)]);

        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN));
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]
                .setOnAction(event -> mainTabPane.getTabs().retainAll(notepadTab));
        menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)].setDisable(true);
        enableOnLoadItems.add(menuItems[fileMenuItems.get(FileMenuItems.CLOSE_ALL_TABS)]);

        return fileMenu;
    }

    /**
     * Wipes all loaded images and PxAttrs, closes open tabs, clears the map list,
     * and disables menu items that only function on loaded mods. In other words,
     * erases all stored and open remnants of the current mod.
     */
    private void wipeLoaded() {
        setTitle("");

        mainTabPane.getTabs().retainAll(notepadTab);
        mapList.getItems().clear();

        for (final MenuItem mItem : enableOnLoadItems) {
            if (!mItem.isDisable()) {
                mItem.setDisable(true);
            }
        }

        ImageManager.wipe();
        PxAttrManager.wipe();
        MapEditTab.wipeImages();
    }

    /**
     * Loads a mod, checking if it is valid. Also creates its assist folder
     *
     * @param executable A {@code File} that references the executable for a mod
     *
     * @throws IOException if the mod could not be initialized or was invalid in some way
     */
    private void loadMod(final Path executable) throws IOException {
        if (null != executable) {
            GameData.init(executable);
            Config.lastExeLoc = executable.toAbsolutePath().toString();

            if (!Files.isWritable(executable.getParent())) {
                //TODO: Figure out how to use POSIX file permissions to give current user RWX permissions
                JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                       Messages.getString("KeroEdit.LoadMod.ReadOnly.TITLE"), null,
                                       Messages.getString("KeroEdit.LoadMod.ReadOnly.MESSAGE"));
            }

            //createAssistFolder();

            loadMapList();

            for (final MenuItem mItem : enableOnLoadItems) {
                if (mItem.isDisable()) {
                    mItem.setDisable(false);
                }
            }

            setTitle(executable.getParent().toAbsolutePath().toString() + File.separatorChar);
        }
    }

    /**
     * Creates the assist folder for a mod inside of its resource folder
     */
    private void createAssistFolder() {
        final Path assistPath = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                          File.separatorChar + "assist");
        try {
            if (!Files.exists(assistPath)) {
                Files.createDirectory(assistPath);
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

            final DirectoryStream <Path> assistPaths = Files.newDirectoryStream(ResourceManager.getPath("assist"));
            for (final Path p : assistPaths) {
                //skip if wrong *_strings.json file
                if (p.toString().endsWith("_strings.json") &&
                    !p.getFileName().toString().equals(stringsFname)) {
                    continue;
                }

                try {
                    final Path destPath = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                    File.separatorChar + "assist" + File.separatorChar +
                                                    p.getFileName());
                    if (!Files.exists(destPath)) {
                        Files.copy(p, destPath);
                    }
                }
                catch (final IOException except) {
                    JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                           Messages.getString("KeroEdit.CreateAssistFolder.CopyFileFail.TITLE"), null,
                                           MessageFormat.format(Messages.getString("KeroEdit.CreateAssistFolder.CopyFileFail.MESSAGE"),
                                                                p.getFileName())).showAndWait();
                }
            }
        }
        catch (final IOException except) {
            JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                   Messages.getString("KeroEdit.CreateAssistFolder.CopyFolderFail.TITLE"),
                                   null,
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
        {
            int i = 0;
            for (final EditMenuItems x : EditMenuItems.values()) {
                editMenuItems.put(x, i++);
            }
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
        {
            int i = 0;
            for (final ViewMenuItems x : ViewMenuItems.values()) {
                viewMenuItems.put(x, i++);
            }
        }

        final RadioMenuItem[] mapZoomMenuItems = createZoomSubmenu(Config.mapZoom);
        int zoom = 2;
        for (final RadioMenuItem mapZoomMItem : mapZoomMenuItems) {
            final int z = zoom;
            mapZoomMItem.setOnAction(event -> MapEditTab.setMapZoom(Config.mapZoom = z));
            zoom += 2;
        }
        ((Menu)menuItems[viewMenuItems.get(ViewMenuItems.MAP_ZOOM)]).getItems().addAll(mapZoomMenuItems);

        final RadioMenuItem[] tilesetZoomMenuItems = createZoomSubmenu(Config.tilesetZoom);
        zoom = 2;
        for (final RadioMenuItem tilesetZoomMItem : tilesetZoomMenuItems) {
            final int z = zoom;
            tilesetZoomMItem.setOnAction(event -> MapEditTab.setTilesetZoom(Config.tilesetZoom = z));
            zoom += 2;
        }
        ((Menu)menuItems[viewMenuItems.get(ViewMenuItems.TILESET_ZOOM)]).getItems().addAll(tilesetZoomMenuItems);

        menuItems[viewMenuItems.get(ViewMenuItems.TILESET_BG_COLOR)].setOnAction(event -> {
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
    private RadioMenuItem[] createZoomSubmenu(final int defaultZoom) {
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

        final EnumMap <ActionsMenuItems, Integer> actionsMenuItems = new EnumMap <>(ActionsMenuItems.class);
        {
            int i = 0;
            for (final ActionsMenuItems x : ActionsMenuItems.values()) {
                actionsMenuItems.put(x, i++);
            }
        }

        menuItems[actionsMenuItems.get(ActionsMenuItems.RUN_GAME)].setOnAction(event -> {
            final Runtime run = Runtime.getRuntime();
            try {
                run.exec(GameData.getExecutable().toAbsolutePath().toString());
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
            final FileChooser scrChooser = new FileChooser();
            scrChooser.setTitle(Messages.getString("KeroEdit.OpenFile.TITLE"));

            scrChooser.setInitialDirectory(new File(GameData.getResourceFolder().toAbsolutePath().toString()));

            final FileChooser.ExtensionFilter[] extFilters =
                    {new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.GlobalScript.SCRIPT_FILTER"),
                                                     "*.pxeve"),
                     new FileChooser.ExtensionFilter(Messages.getString("KeroEdit.GlobalScript.NO_FILTER"),
                                                     "*.*")};
            scrChooser.getExtensionFilters().addAll(extFilters);
            scrChooser.setSelectedExtensionFilter(extFilters[0]);

            final Path scriptFile = scrChooser.showOpenDialog(mainStage).toPath();
            for (final Tab tab : mainTabPane.getTabs()) {
                if (tab instanceof ScriptEditTab &&
                    tab.getId().equals(scriptFile.toAbsolutePath().toString())) {
                    mainTabPane.getSelectionModel().select(tab);
                    mainTabPane.requestFocus();
                    return;
                }

            }

            final ScriptEditTab sEditTab = new ScriptEditTab(scriptFile, true);
            mainTabPane.getTabs().add(sEditTab);
            mainTabPane.getSelectionModel().select(sEditTab);
            mainTabPane.requestFocus();
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
        {
            int i = 0;
            for (final HelpMenuItems x : HelpMenuItems.values()) {
                helpMenuItems.put(x, i++);
            }
        }

        menuItems[helpMenuItems.get(HelpMenuItems.ABOUT)].setOnAction(event -> {
            final Alert aboutAlert = JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                            Messages.getString("KeroEdit.HelpMenu.About.TITLE"), null,
                                                            MessageFormat.format(Messages.getString("KeroEdit.HelpMenu.About.MESSAGE"),
                                                                                 Messages.getString("KeroEdit.LAST_UPDATE"),
                                                                                 Messages.getString("KeroEdit.VERSION")));

            aboutAlert.getDialogPane().setGraphic(new ImageView(ResourceManager.getImage("fdl_logo.png")));
            aboutAlert.showAndWait();
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
    private ListView <String> initMapList() {
        final ListView <String> mapListView = new ListView <>();
        mapListView.setOrientation(Orientation.VERTICAL);

        mapListView.setMinWidth(125);
        mapListView.setPrefWidth(125);

        final MenuItem[] contextMenuItems = {new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.OPEN_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DELETE_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")),
                                             new MenuItem(Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP"))};

        final ContextMenu contextMenu = new ContextMenu(contextMenuItems);
        mapListView.setContextMenu(contextMenu);

        final EnumMap <MapListMenuItems, Integer> mapListMenuItems = new EnumMap <>(MapListMenuItems.class);
        {
            int i = 0;
            for (final MapListMenuItems x : MapListMenuItems.values()) {
                mapListMenuItems.put(x, i++);
            }
        }

        //TODO: Change accelerator text from an arrow to "Enter"
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)].setAccelerator(new KeyCodeCombination(KeyCode.ENTER));
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)].setOnAction(event -> {
            final String filename = mapListView.getSelectionModel().getSelectedItem();
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
        //TODO: Create dialog asking for name
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)]
                .setOnAction(event -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                             Messages.getString("KeroEdit.MapListView.ContextMenu.NEW_MAP")
                                                                     .replace("_", ""),
                                                             null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                .showAndWait());
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.NEW)]);

        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)].setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)].setOnAction(event -> {
            final String mapName = mapListView.getSelectionModel().getSelectedItem();
            JavaFXUtil.createAlert(Alert.AlertType.CONFIRMATION, mapName, null,
                                   "Are you sure you want to delete this map?").showAndWait()
                      .ifPresent(result -> {
                          if (ButtonType.OK == result) {
                              GameData.removeMap(mapName);
                              mapListView.getItems().remove(mapName);

                              //TODO: remove looping part?
                              for (final Tab tab : mainTabPane.getTabs()) {
                                  if (tab instanceof MapEditTab &&
                                      tab.getId().equals(mapName)) {
                                      mainTabPane.getTabs().remove(tab);
                                      break;
                                  }
                              }
                          }
                      });
        });
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.DELETE)]);

        //TODO: Create prompt for new mapname
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.RENAME)]
                .setOnAction(event -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                             Messages.getString("KeroEdit.MapListView.ContextMenu.RENAME_MAP")
                                                                     .replace("_", ""),
                                                             null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                .showAndWait());
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.RENAME)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.RENAME)]);

        //TODO: Create prompt for new mapname
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DUPLICATE)]
                .setOnAction(event -> JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                                             Messages.getString("KeroEdit.MapListView.ContextMenu.DUPLICATE_MAP")
                                                                     .replace("_", ""),
                                                             null, Messages.getString("KeroEdit.NOT_IMPLEMENTED"))
                                                .showAndWait());
        contextMenuItems[mapListMenuItems.get(MapListMenuItems.DUPLICATE)].setDisable(true);
        enableOnLoadItems.add(contextMenuItems[mapListMenuItems.get(MapListMenuItems.DUPLICATE)]);

        mapListView.setOnKeyPressed(event -> {
            /*
             * This code is from before when it seemed that the MenuItems in the context menu weren't being triggered
             * by keypresses on the map list itself.
             * Now it seems they get triggered so I don't think this is necessary anymore, but it's being kept in case
             * it is needed for whatever reason.
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

            //this is the only one that doesn't seem to be triggered by keypresses on map list, IDK why
            if (new KeyCodeCombination(KeyCode.ENTER).match(event)) {
                contextMenuItems[mapListMenuItems.get(MapListMenuItems.OPEN)].getOnAction().handle(new ActionEvent());
            }
        });

        mapListView.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()) {
                contextMenu.getItems().get(mapListMenuItems.get(MapListMenuItems.OPEN))
                           .getOnAction().handle(new ActionEvent());
            }
        });

        return mapListView;
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
        final TabPane tabPane = new TabPane();
        tabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.ALL_TABS);

        tabPane.getTabs().add(notepadTab = new NotepadTab());

        return tabPane;
    }

    /**
     * Prompts the user, asking them if they agree to the license terms (Apache 2.0).
     * Also sets {@code Config.licenseRead}.
     */
    private void showLicense() {
        if (Config.licenseRead) {
            return;
        }

        final Path licensePath = ResourceManager.getPath("LICENSE");

        try (Stream <String> lineStream = Files.lines(licensePath, Charset.forName("UTF-8"))) {
            final String licenseText = lineStream.collect(Collectors.joining("\n"));
            JavaFXUtil.createTextboxAlert(Alert.AlertType.CONFIRMATION,
                                          Messages.getString("KeroEdit.ReadLicense.TITLE"), null,
                                          Messages.getString("KeroEdit.ReadLicense.MESSAGE"),
                                          licenseText, false).showAndWait()
                      .ifPresent(result -> {
                          if (!(Config.licenseRead = (ButtonType.OK == result))) {
                              mainStage.close();
                              Platform.exit();
                              System.exit(0);
                          }
                      });
        }
        catch (final IOException except) { //unlikely
            JavaFXUtil.createAlert(Alert.AlertType.INFORMATION,
                                   Messages.getString("KeroEdit.ReadLicense.UnableToShow.TITLE"), null,
                                   Messages.getString("KeroEdit.ReadLicense.UnableToShow.MESSAGE")).showAndWait();
            Config.licenseRead = true; //allow program use, assuming they read it I guess
        }
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
                checkboxes[i].setSelected(MapEditTab.LayerFlags.values()[i].flag ==
                                          (Config.displayedLayers & MapEditTab.LayerFlags.values()[i].flag));

                int layer = i;
                checkboxes[i].selectedProperty().addListener(((observable, oldValue, newValue) -> {
                    int newDispLayersFlag = displayedLayers.get();
                    if (newValue) {
                        newDispLayersFlag |= MapEditTab.LayerFlags.values()[layer].flag;
                    }
                    else {
                        newDispLayersFlag &= MapEditTab.LayerFlags.values()[layer].flag ^ 0b1111_1111;
                    }

                    displayedLayers.set(newDispLayersFlag);
                    Config.displayedLayers = newDispLayersFlag;
                }));

                add(checkboxes[i], x, y++);
            }

            MapEditTab.bindDisplayedLayers(displayedLayers);
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
            final Text label = new Text(Messages.getString("KeroEdit.SettingsPane.DRAW_MODE"));
            label.setFont(Font.font(null, FontWeight.BOLD, 15));
            add(label, x, y++);

            final ToggleGroup toggleGroup = new ToggleGroup();
            final RadioButton[] radioButtons = {new RadioButton(Messages.getString("KeroEdit.SettingsPane.DRAW"))};

            final EnumMap <DrawSettingsItems, Integer> drawSettingsItems = new EnumMap <>(DrawSettingsItems.class);
            {
                int i = 0;
                for (final DrawSettingsItems k : DrawSettingsItems.values()) {
                    drawSettingsItems.put(k, i++);
                }
            }

            radioButtons[drawSettingsItems.get(DrawSettingsItems.DRAW)].setSelected(true);

            for (int i = 0; i < radioButtons.length; ++i) {
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

    private static final class NotepadTab extends Tab {
        private final TextArea notepad;

        NotepadTab() {
            super(Messages.getString("KeroEdit.NOTEPAD_TITLE"));
            setId(Messages.getString("KeroEdit.NOTEPAD_TITLE"));

            setClosable(false);

            setContent(notepad = new TextArea(Config.notepadText));
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