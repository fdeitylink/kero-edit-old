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
 * Use TornadoFX after finishing Kotlin conversion
 * Use Gradle (Kobalt?)
 * Turn members of Kotlin objects into top-level properties and functions?
 * Don't show tileset stage until after main window is showing (if it is set to be shown from the last session)
 * Before converting MapEditTab, rename its messages.properties strings to match the class hierarchy better
 *  - Also improve the string names in general
 *  - Decide whether to always pass full file path or just filename to the messages
 * ImmutableArray class?
 * Use except.localizedMessage for exceptions that may be visible to the user
 * Override equals in MapEditTab and ScriptEditTab?
 */

package io.fdeitylink.keroedit

import java.util.EnumMap

import java.util.stream.Collectors

import java.text.MessageFormat

import java.io.File

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

import java.nio.charset.Charset

import java.io.IOException
import java.text.ParseException

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

import javafx.application.Application
import javafx.application.Platform

import javafx.stage.Stage
import javafx.stage.Screen
import javafx.scene.Scene

import javafx.scene.control.SplitPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane

import javafx.scene.control.TextArea

import javafx.scene.control.ListView
import javafx.scene.control.ListCell

import javafx.scene.control.SelectionMode

import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuBar
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem

import javafx.scene.control.ToggleGroup
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.CheckBox
import javafx.scene.control.RadioButton

import javafx.scene.control.ButtonType

import javafx.scene.text.Text
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

import javafx.stage.FileChooser
import javafx.scene.control.ColorPicker

import javafx.scene.control.Dialog
import javafx.scene.control.Alert

import javafx.scene.control.TabPane
import javafx.scene.control.Tab

import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCode

import javafx.scene.input.MouseButton

import javafx.event.ActionEvent

import javafx.geometry.Orientation
import javafx.geometry.Insets

import javafx.print.PrinterJob
import javafx.scene.image.ImageView

import io.fdeitylink.util.Logger

import io.fdeitylink.util.baseFilename

import io.fdeitylink.util.use

import io.fdeitylink.util.fx.FXUtil
import io.fdeitylink.util.fx.FXUtil.close
import io.fdeitylink.util.fx.FXUtil.scale
import io.fdeitylink.util.fx.FileEditTab

import io.fdeitylink.keroedit.resource.ResourceManager

import io.fdeitylink.keroedit.gamedata.GameData
import io.fdeitylink.keroedit.gamedata.ModType

import io.fdeitylink.keroedit.map.Layer

import io.fdeitylink.keroedit.image.ImageManager
import io.fdeitylink.keroedit.image.PxAttrManager

import io.fdeitylink.keroedit.hack.HackTab

import io.fdeitylink.keroedit.mapedit.MapEditTab
import io.fdeitylink.keroedit.script.ScriptEditTab

fun main(args: Array<String>) = Application.launch(KeroEdit::class.java, *args)

class KeroEdit : Application() {
    /*
     * TODO:
     * Put all of these properties into the companion object?
     * There will only be one instance of KeroEdit and it
     * would make some code simpler.
     */
    private lateinit var enableOnLoadItems: MutableList<MenuItem>

    private lateinit var openLast: MenuItem

    private lateinit var mainStage: Stage
    private lateinit var mainTabPane: TabPane

    private lateinit var mapList: ListView<Path>

    private lateinit var notepadTab: NotepadTab

    companion object {
        private lateinit var INSTANCE: KeroEdit

        private val baseTitleString = MessageFormat.format(Messages["KeroEdit.APP_TITLE"], Messages["KeroEdit.VERSION"])

        var titleSuffix
            get() = INSTANCE.mainStage.title?.substring(baseTitleString.length) ?: ""
            set(value) {
                INSTANCE.mainStage.title = baseTitleString + value
            }
    }

    override fun init() {
        INSTANCE = this

        //TODO: The handler isn't being executed on non-FX Application threads
        val exceptHandler = Thread.UncaughtExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            Logger.logThrowable("Uncaught exception: ", throwable)
        }
        Thread.currentThread().uncaughtExceptionHandler = exceptHandler
        Thread.setDefaultUncaughtExceptionHandler(exceptHandler)

        Config.load()

        enableOnLoadItems = mutableListOf()
    }

    override fun start(primaryStage: Stage) {
        fun buildScene(): Scene {
            /*
             * Note to self - keep these as BorderPanes - while an HBox and VBox may conceptually seem
             * more fit for this it does not resize well and there's a whole load of sizing issues.
             */
            mainTabPane = initTabPane()
            val right = BorderPane(mainTabPane)
            right.top = SettingsPane()

            mapList = initMapList()
            val sPane = SplitPane(mapList, right)
            sPane.setDividerPositions(0.1)

            val root = BorderPane(sPane)
            root.top = initMenuBar()

            val displayRect = Screen.getPrimary().visualBounds
            return Scene(root, displayRect.width, displayRect.height)
        }

        fun showLicense() {
            if (Config.licenseRead) {
                return
            }

            val licensePath = ResourceManager.getPath("LICENSE")

            if (null != licensePath) {
                try {
                    Files.lines(licensePath, Charset.forName("UTF-8")).use {
                        val licenseText = it.collect(Collectors.joining("\n"))
                        FXUtil.createTextboxAlert(type = Alert.AlertType.CONFIRMATION,
                                                  title = Messages["KeroEdit.ReadLicense.TITLE"],
                                                  message = Messages["KeroEdit.ReadLicense.MESSAGE"],
                                                  textAreaContent = licenseText).showAndWait()
                                .ifPresent {
                                    Config.licenseRead = ButtonType.OK == it
                                    if (!Config.licenseRead) {
                                        Config.save()
                                        Platform.exit()
                                    }
                                }
                        return
                    }
                }
                catch (except: IOException) {
                    Logger.logThrowable("Unable to show license", except)
                    //Jumps to alert creation below
                }
            }

            //Occurs if licensePath is null or an IOException occurred when grabbing the contents of the LICENSE file
            FXUtil.createAlert(type = Alert.AlertType.INFORMATION,
                               title = Messages["KeroEdit.ReadLicense.UnableToShow.TITLE"],
                               message = Messages["KeroEdit.ReadLicense.UnableToShow.MESSAGE"]).showAndWait()
            Config.licenseRead = true //Assume they read it and allow program use
        }

        mainStage = primaryStage
        mainStage.setOnCloseRequest {
            if (!closeTabs()) {
                Platform.exit() //graceful shutdown & closes all child windows
            }
            it.consume()
        }

        mainStage.scene = buildScene()
        mainStage.title = baseTitleString

        mainStage.show()
        mainStage.isMaximized = true
        mainStage.requestFocus()

        showLicense()
    }

    override fun stop() {
        ModLoader.execService.shutdown()
        Config.notepadText = notepadTab.notepad.text
        Config.save()
    }

    private fun initMenuBar(): MenuBar {
        val menuBar = MenuBar()
        menuBar.isUseSystemMenuBar = true
        menuBar.prefWidthProperty().bind(mainStage.widthProperty())

        menuBar.menus.addAll(initFileMenu(), initEditMenu(), initViewMenu(), initActionsMenu(), initHelpMenu())
        return menuBar
    }

    private fun initFileMenu(): Menu {
        val fileMenu = Menu(Messages["KeroEdit.FILE_MENU"])

        val menuItems = EnumMap<FileMenuItem, MenuItem>(FileMenuItem::class.java)
        menuItems.put(FileMenuItem.OPEN, MenuItem(Messages["KeroEdit.FileMenu.OPEN"]))
        menuItems.put(FileMenuItem.OPEN_LAST, MenuItem(Messages["KeroEdit.FileMenu.OPEN_LAST"]))
        menuItems.put(FileMenuItem.SAVE, MenuItem(Messages["KeroEdit.FileMenu.SAVE"]))
        menuItems.put(FileMenuItem.SAVE_ALL, MenuItem(Messages["KeroEdit.FileMenu.SAVE_ALL"]))
        menuItems.put(FileMenuItem.CLOSE_TAB, MenuItem(Messages["KeroEdit.FileMenu.CLOSE_TAB"]))
        menuItems.put(FileMenuItem.CLOSE_ALL_TABS, MenuItem(Messages["KeroEdit.FileMenu.CLOSE_ALL_TABS"]))

        fileMenu.items.addAll(menuItems.values)

        menuItems[FileMenuItem.OPEN]!!.accelerator = KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN)
        menuItems[FileMenuItem.OPEN]!!.setOnAction {
            //If user didn't cancel any attempted tab closes and definitely wants to load new mod
            if (!closeTabs()) {
                ModLoader.wipeLoaded()

                val exeChooser = FileChooser()
                exeChooser.title = Messages["KeroEdit.OpenMod.TITLE"]

                exeChooser.initialDirectory = Config.lastExeLoc.parent.toFile()

                val extFilter = FileChooser.ExtensionFilter(Messages["KeroEdit.OpenMod.EXECUTABLE_FILTER"], "*.exe")
                exeChooser.extensionFilters.add(extFilter)

                val exeFile = exeChooser.showOpenDialog(mainStage)
                if (null != exeFile) {
                    ModLoader.load(exeFile.toPath().toAbsolutePath())
                }
            }
        }

        menuItems[FileMenuItem.OPEN_LAST]!!.accelerator = KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN)
        menuItems[FileMenuItem.OPEN_LAST]!!.setOnAction {
            //If user didn't cancel any attempted tab closes and definitely wants to load last mod
            if (!closeTabs()) {
                ModLoader.wipeLoaded()
                ModLoader.load(Config.lastExeLoc.toAbsolutePath())
            }
        }
        //Disabled if there is no last mod
        menuItems[FileMenuItem.OPEN_LAST]!!.isDisable = !Config.lastExeLoc.toString().endsWith(".exe")
        openLast = menuItems[FileMenuItem.OPEN_LAST]!!

        menuItems[FileMenuItem.SAVE]!!.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)
        menuItems[FileMenuItem.SAVE]!!.setOnAction {
            val selectedTab = mainTabPane.selectionModel.selectedItem
            if (selectedTab is FileEditTab) {
                selectedTab.save()
            }
        }
        menuItems[FileMenuItem.SAVE]!!.isDisable = true
        enableOnLoadItems.add(menuItems[FileMenuItem.SAVE]!!)

        menuItems[FileMenuItem.SAVE_ALL]!!.accelerator =
                KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN)
        menuItems[FileMenuItem.SAVE_ALL]!!.setOnAction {
            mainTabPane.tabs.forEach {
                if (it is FileEditTab) {
                    it.save()
                }
            }
        }
        menuItems[FileMenuItem.SAVE_ALL]!!.isDisable = true
        enableOnLoadItems.add(menuItems[FileMenuItem.SAVE_ALL]!!)

        menuItems[FileMenuItem.CLOSE_TAB]!!.accelerator = KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN)
        menuItems[FileMenuItem.CLOSE_TAB]!!.setOnAction {
            val tab = mainTabPane.selectionModel.selectedItem
            if (null != tab && notepadTab !== tab) {
                tab.close()
            }
        }
        menuItems[FileMenuItem.CLOSE_TAB]!!.isDisable = true
        enableOnLoadItems.add(menuItems[FileMenuItem.CLOSE_TAB]!!)

        menuItems[FileMenuItem.CLOSE_ALL_TABS]!!.accelerator =
                KeyCodeCombination(KeyCode.W, KeyCombination.SHIFT_DOWN, KeyCombination.CONTROL_DOWN)
        menuItems[FileMenuItem.CLOSE_ALL_TABS]!!.setOnAction { closeTabs() }
        menuItems[FileMenuItem.CLOSE_ALL_TABS]!!.isDisable = true
        enableOnLoadItems.add(menuItems[FileMenuItem.CLOSE_ALL_TABS]!!)

        return fileMenu
    }

    private fun initEditMenu(): Menu {
        val editMenu = Menu(Messages["KeroEdit.EDIT_MENU"])

        val menuItems = EnumMap<EditMenuItem, MenuItem>(EditMenuItem::class.java)
        menuItems.put(EditMenuItem.UNDO, MenuItem(Messages["KeroEdit.EditMenu.UNDO"]))
        menuItems.put(EditMenuItem.REDO, MenuItem(Messages["KeroEdit.EditMenu.REDO"]))

        editMenu.items.addAll(menuItems.values)

        menuItems[EditMenuItem.UNDO]!!.accelerator = KeyCodeCombination(KeyCode.Z, KeyCodeCombination.CONTROL_DOWN)
        menuItems[EditMenuItem.UNDO]!!.setOnAction {
            val selectedTab = mainTabPane.selectionModel.selectedItem
            if (selectedTab is FileEditTab) {
                selectedTab.undo()
            }
        }
        menuItems[EditMenuItem.UNDO]!!.isDisable = true
        enableOnLoadItems.add(menuItems[EditMenuItem.UNDO]!!)

        menuItems[EditMenuItem.REDO]!!.accelerator = KeyCodeCombination(KeyCode.Y, KeyCodeCombination.CONTROL_DOWN)
        menuItems[EditMenuItem.REDO]!!.setOnAction {
            val selectedTab = mainTabPane.selectionModel.selectedItem
            if (selectedTab is FileEditTab) {
                selectedTab.redo()
            }
        }
        menuItems[EditMenuItem.REDO]!!.isDisable = true
        enableOnLoadItems.add(menuItems[EditMenuItem.REDO]!!)

        return editMenu
    }

    private fun initViewMenu(): Menu {
        fun buildZoomSubmenu(defaultZoom: Double): Array<RadioMenuItem> {
            val toggleGroup = ToggleGroup()
            val zoomMenuItems = arrayOfNulls<RadioMenuItem>(8)

            var zoom = 0.5
            for (i in zoomMenuItems.indices) {
                zoomMenuItems[i] = RadioMenuItem(Integer.toString((zoom * 100).toInt()) + '%')
                zoomMenuItems[i]!!.toggleGroup = toggleGroup
                zoomMenuItems[i]!!.isSelected = defaultZoom == zoom

                zoom += 0.5
            }

            @Suppress("UNCHECKED_CAST")
            return zoomMenuItems as Array<RadioMenuItem>
        }

        val viewMenu = Menu(Messages["KeroEdit.VIEW_MENU"])

        val menuItems = EnumMap<ViewMenuItem, MenuItem>(ViewMenuItem::class.java)
        menuItems.put(ViewMenuItem.MAP_ZOOM, Menu(Messages["KeroEdit.ViewMenu.MAP_ZOOM"]))
        menuItems.put(ViewMenuItem.TILESET_ZOOM, Menu(Messages["KeroEdit.ViewMenu.TILESET_ZOOM"]))
        menuItems.put(ViewMenuItem.TILESET_BG_COLOR, MenuItem(Messages["KeroEdit.ViewMenu.TILESET_BG_COLOR"]))

        viewMenu.items.addAll(menuItems.values)

        val mapZoomMenuItems = buildZoomSubmenu(Config.mapZoom)
        var zoom = 0.5
        for (mItem in mapZoomMenuItems) {
            val z = zoom
            mItem.setOnAction {
                Config.mapZoom = z
                MapEditTab.setMapZoom(z)
            }
            zoom += 0.5
        }
        (menuItems[ViewMenuItem.MAP_ZOOM] as Menu).items.addAll(*mapZoomMenuItems)

        val tilesetZoomMenuItems = buildZoomSubmenu(Config.tilesetZoom)
        zoom = 0.5
        for (mItem in tilesetZoomMenuItems) {
            val z = zoom
            mItem.setOnAction {
                Config.tilesetZoom = z
                MapEditTab.setTilesetZoom(z)
            }
            zoom += 0.5
        }
        (menuItems[ViewMenuItem.TILESET_ZOOM] as Menu).items.addAll(*tilesetZoomMenuItems)

        menuItems[ViewMenuItem.TILESET_BG_COLOR]!!.setOnAction {
            val cPicker = ColorPicker(Config.tilesetBgColor)
            cPicker.setOnAction {
                Config.tilesetBgColor = cPicker.value
                MapEditTab.setTilesetBgColor(cPicker.value)
            }

            val dialog = Dialog<Unit>()
            dialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)
            dialog.dialogPane.content = cPicker
            dialog.showAndWait()
        }

        return viewMenu
    }

    private fun initActionsMenu(): Menu {
        val actionsMenu = Menu(Messages["KeroEdit.ACTIONS_MENU"])

        val menuItems = EnumMap<ActionsMenuItem, MenuItem>(ActionsMenuItem::class.java)
        menuItems.put(ActionsMenuItem.RUN_GAME, MenuItem(Messages["KeroEdit.ActionsMenu.RUN_GAME"]))
        menuItems.put(ActionsMenuItem.EDIT_GLOBAL_SCRIPT, MenuItem(Messages["KeroEdit.ActionsMenu.EDIT_GLOBAL_SCRIPT"]))
        //menuItems.put(ActionsMenuItem.HACK_EXECUTABLE, MenuItem(Messages["KeroEdit.ActionsMenu.HACK_EXECUTABLE"]))
        menuItems.put(ActionsMenuItem.WAFFLE, MenuItem(Messages["KeroEdit.ActionsMenu.WAFFLE"]))

        actionsMenu.items.addAll(menuItems.values)

        menuItems[ActionsMenuItem.RUN_GAME]!!.accelerator = KeyCodeCombination(KeyCode.F5)
        menuItems[ActionsMenuItem.RUN_GAME]!!.setOnAction {
            try {
                /*
                 * TODO:
                 * Prompt to save unsaved changes before running?
                 * Run in background thread
                 */
                //TODO: Prompt to save unsaved changes before running?
                Runtime.getRuntime().exec(GameData.executable.toString())
            }
            catch (except: IOException) {
                FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                   title = Messages["KeroEdit.RunGame.IOExcept.TITLE"],
                                   message = Messages["KeroEdit.RunGame.IOExcept.MESSAGE"]).showAndWait()
            }
        }
        menuItems[ActionsMenuItem.RUN_GAME]!!.isDisable = true
        enableOnLoadItems.add(menuItems[ActionsMenuItem.RUN_GAME]!!)

        menuItems[ActionsMenuItem.EDIT_GLOBAL_SCRIPT]!!.setOnAction {
            val scriptChooser = FileChooser()
            scriptChooser.title = Messages["KeroEdit.GlobalScript.TITLE"]

            scriptChooser.initialDirectory = File(GameData.resourceFolder.toString())

            val extFilters = arrayOf(FileChooser.ExtensionFilter(Messages["KeroEdit.GlobalScript.SCRIPT_FILTER"], "*.pxeve"),
                                     FileChooser.ExtensionFilter(Messages["KeroEdit.GlobalScript.NO_FILTER"], "*.*"))
            scriptChooser.extensionFilters.addAll(*extFilters)
            scriptChooser.selectedExtensionFilter = extFilters[0]

            val scriptFiles = scriptChooser.showOpenMultipleDialog(mainStage)
            if (null != scriptFiles) {
                for (f in scriptFiles) {
                    val scriptPath = f.toPath().toAbsolutePath()
                    var isAlreadyOpen = false

                    val tabs = mainTabPane.tabs
                    for (tab in tabs) {
                        if ((tab is ScriptEditTab && tab.path == scriptPath) ||
                            (tab is MapEditTab && tab.scriptPath == scriptPath)) {
                            mainTabPane.selectionModel.select(tab)
                            mainTabPane.requestFocus() //TODO: Select ScriptEditTab in the MapEditTab?
                            isAlreadyOpen = true
                            break
                        }
                    }

                    if (!isAlreadyOpen) {
                        try {
                            val scriptEditTab = ScriptEditTab(scriptPath)

                            mainTabPane.tabs.add(scriptEditTab)
                            mainTabPane.selectionModel.select(scriptEditTab)
                            mainTabPane.requestFocus()
                        }
                        catch (except: IOException) {
                            /*
                             * Do nothing - exception just signals that there was a script reading issue
                             * and prevents us from adding the ScriptEditTab to the tab pane.
                             * A dialog was already shown to the user via the ScriptEditTab constructor.
                             */
                        }
                    }
                }
            }
        }
        menuItems[ActionsMenuItem.EDIT_GLOBAL_SCRIPT]!!.isDisable = true
        enableOnLoadItems.add(menuItems[ActionsMenuItem.EDIT_GLOBAL_SCRIPT]!!)

        /*menuItems[ActionsMenuItem.HACK_EXECUTABLE]!!.setOnAction {
            if (HackTab.INSTANCE !in mainTabPane.tabs) {
                mainTabPane.tabs.add(HackTab)
            }
            mainTabPane.selectionModel.select(HackTab.INSTANCE)
            mainTabPane.requestFocus()
        }
        menuItems[ActionsMenuItem.HACK_EXECUTABLE]!!.isDisable = true
        enableOnLoadItems.add(menuItems[ActionsMenuItem.HACK_EXECUTABLE]!!)*/

        menuItems[ActionsMenuItem.WAFFLE]!!.setOnAction {
            val errorAlert by lazy(LazyThreadSafetyMode.NONE) {
                FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                   title = Messages["KeroEdit.WaffleError.TITLE"],
                                   message = Messages["KeroEdit.WaffleError.MESSAGE"])
            }

            val printJob = PrinterJob.createPrinterJob()
            if (null == printJob) {
                errorAlert.showAndWait()
                return@setOnAction
            }

            //TODO: Print preview, etc.?
            if (printJob.showPrintDialog(mainStage)) {
                if (printJob.printPage(ImageView(ResourceManager.getImage("waffle.png").scale(32.0)))) {
                    printJob.endJob()
                }
                else {
                    errorAlert.showAndWait()
                }
            }
        }

        return actionsMenu
    }

    private fun initHelpMenu(): Menu {
        val helpMenu = Menu(Messages["KeroEdit.HELP_MENU"])

        val menuItems = EnumMap<HelpMenuItem, MenuItem>(HelpMenuItem::class.java)
        menuItems.put(HelpMenuItem.ABOUT, MenuItem(Messages["KeroEdit.HelpMenu.ABOUT"]))
        //menuItems.put(HelpMenuItem.GUIDE, MenuItem(Messages["KeroEdit.HelpMenu.GUIDE"]))

        helpMenu.items.addAll(menuItems.values)

        menuItems[HelpMenuItem.ABOUT]!!.setOnAction {
            val aboutAlert = FXUtil.createAlert(type = Alert.AlertType.INFORMATION,
                                                title = Messages["KeroEdit.HelpMenu.About.TITLE"],
                                                message = MessageFormat.format(Messages["KeroEdit.HelpMenu.About.MESSAGE"],
                                                                               Messages["KeroEdit.LAST_UPDATE"],
                                                                               Messages["KeroEdit.VERSION"]))
            aboutAlert.graphic = ImageView(ResourceManager.getImage("fdl_logo.png"))
            aboutAlert.showAndWait()
        }

        return helpMenu
    }

    private fun initMapList(): ListView<Path> {
        //TODO: Make cells editable and rename maps after user is done editing cell?
        val mapList = ListView<Path>()
        mapList.orientation = Orientation.VERTICAL
        mapList.selectionModel.selectionMode = SelectionMode.MULTIPLE

        mapList.setCellFactory {
            object : ListCell<Path>() {
                override fun updateItem(map: Path?, empty: Boolean) {
                    super.updateItem(map, empty)
                    text = if (empty || null == map) null else map.baseFilename(GameData.mapExtension)
                }
            }
        }

        val contextMenuItems = EnumMap<MapListMenuItem, MenuItem>(MapListMenuItem::class.java)
        contextMenuItems.put(MapListMenuItem.OPEN, MenuItem(Messages["KeroEdit.MapList.OPEN"]))
        //contextMenuItems.put(MapListMenuItem.NEW, MenuItem(Messages["KeroEdit.MapList.NEW"]))
        contextMenuItems.put(MapListMenuItem.DELETE, MenuItem(Messages["KeroEdit.MapList.DELETE"]))
        //contextMenuItems.put(MapListMenuItem.RENAME, MenuItem(Messages["KeroEdit.MapList.RENAME"]))
        //contextMenuItems.put(MapListMenuItem.DUPLICATE, MenuItem(Messages["KeroEdit.MapList.DUPLICATE"]))

        //TODO: Any way to directly pass list to varargs?
        mapList.contextMenu = ContextMenu(*contextMenuItems.values.toTypedArray())

        contextMenuItems[MapListMenuItem.OPEN]!!.accelerator = KeyCodeCombination(KeyCode.ENTER)
        contextMenuItems[MapListMenuItem.OPEN]!!.setOnAction {
            val selectedItems = mapList.selectionModel.selectedItems
            for (map in selectedItems) {
                var isAlreadyOpen = false
                for (tab in mainTabPane.tabs) {
                    if (tab is MapEditTab && tab.path == map) {
                        mainTabPane.selectionModel.select(tab)
                        mainTabPane.requestFocus()
                        isAlreadyOpen = true
                        break
                    }
                }

                if (!isAlreadyOpen) {
                    try {
                        /*
                         * If the script for this map is already open in mainTabPane, close it so that
                         * two copies aren't open. The map's filename (sans extension) is equivalent to
                         * the script's filename (sans extension)
                         */
                        for (tab in mainTabPane.tabs) {
                            if (tab is ScriptEditTab &&
                                tab.path.baseFilename(GameData.scriptExtension) == map.baseFilename(GameData.mapExtension)) {
                                mainTabPane.tabs.remove(tab) //TODO: Use closeTab(tab) to trigger onCloseRequest()?
                                break
                            }
                        }

                        val mapEditTab = MapEditTab(map)

                        mainTabPane.tabs.add(mapEditTab)
                        mainTabPane.selectionModel.select(mapEditTab)
                        mainTabPane.requestFocus()
                    }
                    catch (except: Exception) {
                        /*
                         * Do nothing when it is an IOException or ParseException - the exception
                         * just signals that there was a map parsing or script reading issue and
                         * prevents us from adding the MapEditTab to the tab pane. A dialog was
                         * already shown to the user via the MapEditTab constructor.
                         */
                        if (except !is IOException && except !is ParseException) {
                            throw except
                        }
                    }
                }
            }
        }
        contextMenuItems[MapListMenuItem.OPEN]!!.isDisable = true
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.OPEN]!!)

        contextMenuItems[MapListMenuItem.DELETE]!!.accelerator = KeyCodeCombination(KeyCode.DELETE)
        contextMenuItems[MapListMenuItem.DELETE]!!.setOnAction {
            //This code works similarly to that in closeTabs() so reading that may help you to understand this

            /*
             * Reference new list to avoid list being modified because of deleted and deselected items
             * the List maintained by the ListView's SelectionModel doesn't properly adjust to insertions
             * and deletions and ends up being a pain to work with
             */
            //TODO: Avoid creating a new List if feasible
            val selectedMaps = ArrayList(mapList.selectionModel.selectedItems)
            val i = 0
            while (i < selectedMaps.size && 0 < selectedMaps.size) {
                val map = selectedMaps[i]
                FXUtil.createAlert(type = Alert.AlertType.CONFIRMATION,
                                   title = map.baseFilename(GameData.mapExtension),
                                   message = Messages["KeroEdit.DeleteMap.MESSAGE"]).showAndWait()
                        .ifPresent {
                            if (ButtonType.OK == it) {
                                /*
                                 * GameData.maps is the backing list for the ListView, so
                                 * changes to the ListView's items affect GameData and vice versa.
                                 */
                                mapList.items.remove(map)
                                selectedMaps.remove(map)

                                /*
                                 * Close any MapEditTab or ScriptEditTab bearing the filename of this map.
                                 * We can break as soon as we find it because there will never be a
                                 * MapEditTab and ScriptEditTab open at the same time that edit the same
                                 * script.
                                 */
                                for (tab in mainTabPane.tabs) {
                                    if ((tab is MapEditTab && tab.path == map) ||
                                        (tab is ScriptEditTab &&
                                         tab.path.baseFilename(GameData.scriptExtension) == map.baseFilename(GameData.mapExtension))) {
                                        /*
                                         * Don't use close() from FXUtil as we've already confirmed
                                         * the user wants to delete the map, and thus that they
                                         * don't care about unsaved changes.
                                         */
                                        mainTabPane.tabs.remove(tab)
                                        break
                                    }
                                }
                            }
                        }

                //If user canceled deleting a map, don't attempt to delete any others
                if (selectedMaps.contains(map)) {
                    return@setOnAction
                }
            }

            mapList.selectionModel.clearSelection()
        }
        contextMenuItems[MapListMenuItem.DELETE]!!.isDisable = true
        enableOnLoadItems.add(contextMenuItems[MapListMenuItem.DELETE]!!)

        mapList.setOnKeyPressed {
            val openMapItem = contextMenuItems[MapListMenuItem.OPEN]!!
            if (openMapItem.accelerator.match(it)) {
                openMapItem.onAction.handle(ActionEvent())
            }
        }

        mapList.setOnMouseClicked {
            if (MouseButton.PRIMARY == it.button && 2 == it.clickCount) {
                contextMenuItems[MapListMenuItem.OPEN]!!.onAction.handle(ActionEvent())
            }
        }

        return mapList
    }

    private fun initTabPane(): TabPane {
        val tabPane = TabPane()
        tabPane.tabClosingPolicy = TabPane.TabClosingPolicy.ALL_TABS

        notepadTab = NotepadTab()
        tabPane.tabs.add(notepadTab)

        return tabPane
    }

    private fun closeTabs(): Boolean {
        val tabs = mainTabPane.tabs
        var i = 0
        while (i < tabs.size && 0 < tabs.size) {
            val tab = tabs[i]
            if (notepadTab == tab) {
                i++
                continue
            }

            mainTabPane.selectionModel.select(tab)
            tab.close()

            /*
             * If tab is a FileEditTab it won't actually close if the user presses
             * cancel on the unsaved changes prompt. So if they did cancel closing
             * a tab, quit immediately.
             */
            if (tabs.contains(tab)) {
                return true
            }
        }

        return false
    }

    private object ModLoader {
        val execService: ExecutorService = Executors.newSingleThreadExecutor()

        private lateinit var executable: Path

        /**
         * Erases all stored and open remnants of the currently loaded mod. By now
         * the user should have saved or discarded their work and confirmed that
         * they want to load a new mod.
         */
        fun wipeLoaded() {
            titleSuffix = ""

            INSTANCE.enableOnLoadItems.filterNot { it.isDisable }.forEach { it.isDisable = true }

            GameData.wipe()
            HackTab.wipe()
            ImageManager.wipe()
            PxAttrManager.wipe()
            MapEditTab.wipeResources()

            //TODO: Call closeTabs()? Close tabs with tabs.remove()?
        }

        fun load(executable: Path) {
            if (!executable.toString().endsWith(".exe")) {
                FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                   title = Messages["KeroEdit.LoadMod.NotExe.TITLE"],
                                   message = MessageFormat.format(Messages["KeroEdit.LoadMod.NotExe.MESSAGE"],
                                                                  executable)).showAndWait()
                return
            }

            if (!Files.exists(executable)) {
                FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                   title = Messages["KeroEdit.LoadMod.Missing.TITLE"],
                                   message = MessageFormat.format(Messages["KeroEdit.LoadMod.Missing.MESSAGE"],
                                                                  executable)).showAndWait()
                return
            }

            this.executable = executable.toAbsolutePath()
            execService.submit(this::load)
        }

        private fun createAssistFolder() {
            try {
                val modAssistPath = Paths.get(GameData.resourceFolder.toString() +
                                              File.separatorChar + "assist")
                if (!Files.exists(modAssistPath)) {
                    Files.createDirectory(modAssistPath)
                }

                //Jumps to catch block below if null
                val internalAssistPath = ResourceManager.getPath("assist") ?: throw IOException()

                val stringsFname =
                        when (GameData.modType) {
                            ModType.PINK_HOUR -> "hour_strings.json"
                            ModType.PINK_HEAVEN -> "heaven_strings.json"
                            ModType.KERO_BLASTER -> "kero_strings.json"
                        }

                Files.newDirectoryStream(internalAssistPath, {
                    (!it.toString().endsWith("_strings.json") || it.fileName.toString() == stringsFname) &&
                    "unittype.txt" != it.fileName.toString()
                }).use {
                    for (p in it) {
                        try {
                            val modCopyPath = Paths.get(modAssistPath.toString() +
                                                        File.separatorChar + p.fileName)
                            if (!Files.exists(modCopyPath)) {
                                Files.copy(p.toAbsolutePath(), modCopyPath)
                            }
                        }
                        catch (except: IOException) {
                            Logger.logThrowable("Failed to copy assist file ${p.toAbsolutePath()}", except)
                            Platform.runLater {
                                FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                                   title = Messages["KeroEdit.CreateAssist.CopyFileFail.TITLE"],
                                                   message = MessageFormat.format(Messages["KeroEdit.CreateAssist.CopyFileFail.MESSAGE"],
                                                                                  p.toAbsolutePath())).showAndWait()
                            }
                            //TODO: Skip the rest of the files?
                        }
                    }
                }
            }
            catch (except: IOException) {
                Logger.logThrowable("Failed to create assist folder", except)
                Platform.runLater {
                    FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                       title = Messages["KeroEdit.CreateAssist.CreateFolderFail.TITLE"],
                                       message = Messages["KeroEdit.CreateAssist.CreateFolderFail.MESSAGE"]).showAndWait()
                }
            }
        }

        private fun load() {
            try {
                GameData.init(executable)
                Config.lastExeLoc = executable

                val executableParent = executable.parent.toAbsolutePath().toFile()

                var hasRWXPermissions = executableParent.canRead() &&
                                        executableParent.canWrite() &&
                                        executableParent.canExecute()

                if (!hasRWXPermissions) {
                    hasRWXPermissions = executableParent.setReadable(true) &&
                                        executableParent.setWritable(true) &&
                                        executableParent.setExecutable(true)
                    if (!hasRWXPermissions) {
                        Platform.runLater {
                            FXUtil.createAlert(type = Alert.AlertType.INFORMATION,
                                               title = Messages["KeroEdit.LoadMod.StrictPermissions.TITLE"],
                                               message = Messages["KeroEdit.LoadMod.StrictPermissions.MESSAGE"])
                                    .showAndWait()
                        }
                    }
                }

                if (hasRWXPermissions) {
                    createAssistFolder()
                }

                HackTab.init()

                Platform.runLater {
                    titleSuffix = " - " + executable.parent.toAbsolutePath() + File.separatorChar

                    INSTANCE.openLast.isDisable = false

                    INSTANCE.mapList.items = GameData.maps
                    INSTANCE.mapList.requestFocus()

                    INSTANCE.enableOnLoadItems.filter { it.isDisable }.forEach { it.isDisable = false }
                }
            }
            catch (except: IOException) {
                Platform.runLater {
                    FXUtil.createAlert(type = Alert.AlertType.ERROR,
                                       title = Messages["KeroEdit.LoadMod.IOExcept.TITLE"],
                                       message = except.message).showAndWait()
                }
            }
        }
    }
}

private class SettingsPane : GridPane() {
    companion object {
        private val font = Font.font(null, FontWeight.BOLD, 15.0)
    }

    init {
        padding = Insets(10.0, 10.0, 10.0, 10.0)
        vgap = 10.0
        hgap = 20.0

        var x = 0
        initDisplayedLayers(x++)
        initSelectedLayer(x++)
        initDrawModes(x++)
        initViewSettings(x++)
        initEditMode(x)
    }

    private fun initDisplayedLayers(x: Int) {
        var y = 0

        val label = Text(Messages["KeroEdit.SettingsPane.DISPLAYED_LAYERS"])
        label.font = font
        add(label, x, y++)

        val cBoxes = arrayOf(CheckBox(Messages["PxPack.LayerNames.FOREGROUND"]),
                             CheckBox(Messages["PxPack.LayerNames.MIDDLEGROUND"]),
                             CheckBox(Messages["PxPack.LayerNames.BACKGROUND"]))

        for (layer in cBoxes.indices) {
            cBoxes[layer].isAllowIndeterminate = false
            //selected if the flag is set
            cBoxes[layer].isSelected = Config.displayedLayers.contains(Layer.values()[layer])

            cBoxes[layer].setOnAction {
                val flag = Layer.values()[layer]
                if (cBoxes[layer].isSelected) {
                    Config.displayedLayers.add(flag)
                }
                else {
                    Config.displayedLayers.remove(flag)
                }
                MapEditTab.setDisplayedLayers(Config.displayedLayers)
            }

            add(cBoxes[layer], x, y++)
        }

        /*
         * TODO:
         * Why is this necessary for setOnAction{} to properly trigger a property change
         * the first time the user clicks a checkbox?
         */
        MapEditTab.setDisplayedLayers(Config.displayedLayers)
    }

    private fun initSelectedLayer(x: Int) {
        var y = 0

        val label = Text(Messages["KeroEdit.SettingsPane.SELECTED_LAYER"])
        label.font = font
        add(label, x, y++)

        val toggleGroup = ToggleGroup()
        val radioButtons = arrayOf(RadioButton(Messages["PxPack.LayerNames.FOREGROUND"]),
                                   RadioButton(Messages["PxPack.LayerNames.MIDDLEGROUND"]),
                                   RadioButton(Messages["PxPack.LayerNames.BACKGROUND"]))

        radioButtons[Config.selectedLayer.ordinal].isSelected = true

        for (i in radioButtons.indices) {
            radioButtons[i].toggleGroup = toggleGroup

            radioButtons[i].selectedProperty().addListener { _, _, newValue ->
                if (newValue) {
                    val layer = Layer.values()[i]
                    MapEditTab.setSelectedLayer(layer)
                    Config.selectedLayer = layer
                }
            }

            add(radioButtons[i], x, y++)
        }
    }

    private fun initDrawModes(x: Int) {
        var y = 0

        val label = Text(Messages["KeroEdit.SettingsPane.DRAW_MODE"])
        label.font = font
        add(label, x, y++)

        val toggleGroup = ToggleGroup()
        val radioButtons = arrayOf(RadioButton(Messages["KeroEdit.SettingsPane.DRAW"])/*,
                                       RadioButton(Messages["KeroEdit.SettingsPane.RECT"]),
                                       RadioButton(Messages["KeroEdit.SettingsPane.COPY"]),
                                       RadioButton(Messages["KeroEdit.SettingsPane.FILL"]),
                                       RadioButton(Messages["KeroEdit.SettingsPane.REPLACE"])*/)

        radioButtons[Config.drawMode.ordinal].isSelected = true

        for (i in radioButtons.indices) {
            radioButtons[i].toggleGroup = toggleGroup

            radioButtons[i].selectedProperty().addListener { _, _, _ ->
                val mode = MapEditTab.DrawMode.values()[i]
                MapEditTab.setDrawMode(mode)
                Config.drawMode = mode
            }

            add(radioButtons[i], x, y++)
        }
    }

    private fun initViewSettings(x: Int) {
        var y = 0

        val label = Text(Messages["KeroEdit.SettingsPane.VIEW_SETTINGS"])
        label.font = font
        add(label, x, y++)

        val cBoxes = arrayOf(CheckBox(Messages["KeroEdit.SettingsPane.TILE_TYPES"]),
                             CheckBox(Messages["KeroEdit.SettingsPane.GRID"]),
                             CheckBox(Messages["KeroEdit.SettingsPane.ENTITY_BOXES"]),
                             CheckBox(Messages["KeroEdit.SettingsPane.ENTITY_SPRITES"])/*,
                             CheckBox(Messages["KeroEdit.SettingsPane.ENTITY_NAMES"])*/)

        for (i in cBoxes.indices) {
            cBoxes[i].isAllowIndeterminate = false

            cBoxes[i].isSelected = Config.viewSettings.contains(MapEditTab.ViewOption.values()[i])

            cBoxes[i].setOnAction {
                val flag = MapEditTab.ViewOption.values()[i]
                if (cBoxes[i].isSelected) {
                    Config.viewSettings.add(flag)
                }
                else {
                    Config.viewSettings.remove(flag)
                }

                MapEditTab.setViewSettings(Config.viewSettings)
            }

            add(cBoxes[i], x, y++)
        }

        /*
         * TODO:
         * Why is this necessary for setOnAction{} to properly trigger a property change
         * the first time the user clicks a checkbox?
         */
        MapEditTab.setViewSettings(Config.viewSettings)
    }

    private fun initEditMode(x: Int) {
        var y = 0

        val label = Text(Messages["KeroEdit.SettingsPane.EDIT_MODE"])
        label.font = font
        add(label, x, y++)

        val toggleGroup = ToggleGroup()
        val radioButtons = arrayOf(RadioButton(Messages["KeroEdit.SettingsPane.TILE"]),
                                   RadioButton(Messages["KeroEdit.SettingsPane.ENTITY"]))

        radioButtons[Config.editMode.ordinal].isSelected = true

        for (i in radioButtons.indices) {
            radioButtons[i].toggleGroup = toggleGroup

            radioButtons[i].selectedProperty().addListener { _, _, _ ->
                val mode = MapEditTab.EditMode.values()[i]
                MapEditTab.setEditMode(mode)
                Config.editMode = mode
            }

            add(radioButtons[i], x, y++)
        }
    }
}

private class NotepadTab : Tab(Messages["KeroEdit.NOTEPAD_TITLE"]) {
    val notepad: TextArea = TextArea(Config.notepadText)

    init {
        id = text
        isClosable = false
        content = notepad
    }
}

private enum class FileMenuItem {
    OPEN,
    OPEN_LAST,
    SAVE,
    SAVE_ALL,
    CLOSE_TAB,
    CLOSE_ALL_TABS
}

private enum class EditMenuItem {
    UNDO,
    REDO
}

private enum class ViewMenuItem {
    MAP_ZOOM,
    TILESET_ZOOM,
    TILESET_BG_COLOR
}

private enum class ActionsMenuItem {
    RUN_GAME,
    EDIT_GLOBAL_SCRIPT,
    HACK_EXECUTABLE,
    WAFFLE
}

private enum class HelpMenuItem {
    ABOUT,
    GUIDE
}

private enum class MapListMenuItem {
    OPEN,
    NEW,
    DELETE,
    DUPLICATE,
    RENAME //TODO: remove this?
}