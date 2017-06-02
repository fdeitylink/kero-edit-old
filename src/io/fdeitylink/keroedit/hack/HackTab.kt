package io.fdeitylink.keroedit.hack

import java.io.File

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

import java.io.IOException

import java.nio.charset.Charset

import javafx.scene.control.SplitPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

import javafx.scene.control.TreeView
import javafx.scene.control.TreeItem

import javafx.geometry.Insets

import javafx.scene.text.Text
import javafx.scene.text.Font

import javafx.scene.control.TextField

import javafx.scene.control.Tooltip

import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonArray
import com.eclipsesource.json.JsonValue
import com.eclipsesource.json.ParseException

import io.fdeitylink.util.fx.FXUtil.setMaxLen

import io.fdeitylink.util.fx.FileEditTab

import io.fdeitylink.keroedit.resource.ResourceManager

import io.fdeitylink.keroedit.Messages

import io.fdeitylink.keroedit.gamedata.GameData
import io.fdeitylink.keroedit.gamedata.ModType

/*
 * TODO:
 * Update executable in init() (using a rename method()?)
 */
object HackTab: FileEditTab(Paths.get("")) {
    var isInitialized = false

    private val sPane = SplitPane()

    init {
        tabPaneProperty()
                .addListener({ _, _, newValue ->
                                 if (null != newValue && !isInitialized) {
                                     tabPane.tabs.remove(this)
                                     throw IllegalStateException("HackTab must be initialized before being added to a TabPane")
                                 }
                             })
    }

    fun init() {
        wipe()

        if (!GameData.isInitialized) {
            throw IllegalStateException("GameData must be initialized before HackTab can be initialized")
        }

        isInitialized = true

        /*
         * TODO:
         * Iterate through every Path in the assist/hacks folder (create this folder, by the way)
         *  - Ensure only the proper *_strings.json file is used
         * Create a new HackTreeItem for every file and add it to the root
         */

        val stringsFname =
                when (GameData.modType) {
                    ModType.PINK_HOUR -> "hour_strings.json"
                    ModType.PINK_HEAVEN -> "heaven_strings.json"
                    ModType.KERO_BLASTER -> "kero_strings.json"
                }

        //TODO: Create const val assistFolder = "assist" in one of the classes (KeroEdit?)
        var stringsPath = Paths.get(GameData.resourceFolder.toString() +
                                    File.separatorChar + "assist" + File.separatorChar +
                                    stringsFname)

        if (!Files.exists(stringsPath)) {
            stringsPath = ResourceManager.getPath("assist/" + stringsFname) ?: return //continue
        }

        val stringsTreeItem = parseHackFile(stringsPath)

        val root = HackTreeItem()
        root.isExpanded = true
        root.children.addAll(arrayOf(stringsTreeItem))

        //TODO: Create HackTreeView? (subclass of TreeView<String>)
        val hacksTree = TreeView<String>(root)
        hacksTree.selectionModel.selectedItemProperty()
                .addListener({ _, _, newValue ->
                                 if (newValue.isLeaf) {
                                     sPane.items[1] = (newValue as HackTreeItem).hackPane
                                 }
                             })

        /*
         * Get first item of root, which only has children and then get
         * the first item of that "subroot", which also only has children.
         * That will be a HackTreeItem - add its hackPane to sPane
         */
        sPane.items.add(hacksTree)
        if (root.children.isNotEmpty()) {
            val subrootChildren = root.children[0].children
            if (subrootChildren.isNotEmpty()) {
                sPane.items.add((subrootChildren[0] as HackTreeItem).hackPane)
                sPane.setDividerPositions(0.2)
            }
        }

        //TODO: Add 'Apply' button

        text = Messages.getString("HackTab.TITLE")
        tooltip = Tooltip(GameData.executable.toString())

        content = sPane
    }

    fun wipe() {
        isInitialized = false
        sPane.items.clear()
    }

    //Does nothing yet
    override fun save() = Unit

    private fun parseHackFile(hackFilePath: Path): HackTreeItem? {
        var subrootName = "<name missing>"
        var hackTreeItems = emptyArray<HackTreeItem?>()

        try {
            Files.newBufferedReader(hackFilePath, Charset.forName("UTF-8")).use {
                //TODO: Check that result of Json.parse(it) is an object (as JSON spec also allows array as root)
                val topLevelObj = Json.parse(it).asObject()
                subrootName = topLevelObj.getString("name", "<name missing>")

                val sectsVal: JsonValue? = topLevelObj.get("sects")
                val sects: JsonArray
                if (null == sectsVal || !sectsVal.isArray) {
                    return@use
                }

                sects = sectsVal.asArray()
                hackTreeItems = arrayOfNulls(sects.size())

                var i = 0
                for (sect in sects) {
                    if (!sect.isObject) {
                        continue
                    }

                    val itemsVal: JsonValue? = sect.asObject().get("items")
                    if (null == itemsVal || !itemsVal.isArray) {
                        continue
                    }
                    val items = itemsVal.asArray()

                    val hackPane = VBox(10.0)

                    for (item in items) {
                        if (!item.isObject) {
                            continue
                        }

                        val label = item.asObject().getString("label", "<label missing>")

                        //TODO: Change this so it reads a String from the exe. Be sure to strip null terminators
                        val currentVal = item.asObject().getString("default", "")
                        val defaultVal = item.asObject().getString("default", "<default missing>")
                        val len = item.asObject().getInt("len", -1)
                        val offset = item.asObject().getInt("offset", -1) //TODO: Use Long?

                        /*
                         * TODO:
                         * Instead of using HackField, put everything into a GridPane so
                         * it lines up well. Find some way to retain the necessary data
                         * for saving (offset and maybe len).
                         */
                        hackPane.children.add(HackField(label, currentVal, defaultVal, len, offset))
                    }

                    val name = sect.asObject().getString("name", null)
                    hackTreeItems[i++] = HackTreeItem(name, hackPane)
                }
            }
        }
        catch (except: IOException) {
            //TODO: Show error alert
            except.printStackTrace()
            return null
        }
        catch (except: ParseException) {
            //TODO: Show error alert
            except.printStackTrace()
            return null
        }

        val subroot = HackTreeItem(subrootName)
        subroot.isExpanded = true
        subroot.children.addAll(hackTreeItems.filterNotNull())

        return subroot
    }
}

//TODO: Make this extend TreeItem<VBox>? TreeItem<HackPane> where HackPane extends VBox and overrides toString()?
private class HackTreeItem(name: String?, val hackPane: VBox?): TreeItem<String>(name) {
    constructor(): this(null, null)
    constructor(name: String): this(name, null)
}

private class HackField(labelText: String, currentVal: String, defaultVal: String,
                        len: Int, private val offset: Int): HBox(10.0) {
    private val field: TextField

    init {
        padding = Insets(10.0, 10.0, 10.0, 10.0)

        val label = Text(labelText)
        label.font = Font.font(12.0)

        field = TextField(currentVal)
        field.isDisable = (-1 == len || -1 == offset)

        //TODO: Messages.properties string for this
        field.tooltip = Tooltip("Default: $defaultVal")
        field.setMaxLen(len)

        children.addAll(label, field)
    }
}