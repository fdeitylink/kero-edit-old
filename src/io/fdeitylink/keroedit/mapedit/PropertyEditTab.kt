package io.fdeitylink.keroedit.mapedit

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

import javafx.scene.Node

import javafx.scene.layout.GridPane

import javafx.geometry.Insets

import javafx.scene.text.Text
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

import javafx.scene.control.TextField

import javafx.scene.control.ComboBox
import javafx.scene.control.ListCell

import io.fdeitylink.util.baseFilename

import io.fdeitylink.util.fx.FXUtil.setMaxLen

import io.fdeitylink.util.fx.FileEditTab

import io.fdeitylink.keroedit.Messages

import io.fdeitylink.keroedit.gamedata.GameData

import io.fdeitylink.keroedit.map.PxPack

class PropertyEditTab(private val parent: MapEditTab, private val map: PxPack
) : FileEditTab(map.path, Messages["PropertyEditTab.TITLE"]) {
    init {
        /*
         * TODO:
         * For values that can be blank, put a blank item
         * into the list, or a button that clears the selection.
         *
         * Applies to
         *  - Mapnames
         *  - Spritesheet (maybe)
         *  - Second two tilesets
         *
         * Label of button cells for the ComboBoxes should be just
         * the filename, not the full path.
         */

        data class Field(val label: Text, val field: Node)

        val font = Font.font(null, FontWeight.NORMAL, 12.0)

        fun initDescription(): Field {
            val label = Text("Description").also { it.font = font }

            val textField = TextField(map.head.description).also {
                it.setMaxLen(PxPack.Head.DESCRIPTION_MAX_LEN)
                textProperty().addListener { _, _, newValue ->
                    map.head.description = newValue
                    markChanged()
                    parent.tooltip.text = "${map.path}\n${Messages["MapEditTab.TOOLTIP_DESCRIPTION_LABEL"]} $newValue"
                }
            }

            return Field(label, textField)
        }

        fun initMapnames(): Array<Field> {
            val mapnames = map.head.mapNames

            val fields = arrayOfNulls<Field>(mapnames.size)

            for (i in mapnames.indices) {
                val cBox = ComboBox(GameData.maps)

                //TODO: Make one Callback object that is used for multiple ComboBoxes
                cBox.setCellFactory {
                    object : ListCell<Path>() {
                        override fun updateItem(map: Path, empty: Boolean) {
                            super.updateItem(map, empty)
                            text = if (empty) null else map.baseFilename(GameData.mapExtension)
                        }
                    }
                }

                val mapPath = Paths.get(GameData.resourceFolder.toString() +
                                        File.separatorChar + GameData.mapFolder + File.separatorChar +
                                        mapnames[i] + GameData.mapExtension)

                with(cBox.selectionModel) {
                    select(mapPath)

                    val index = i
                    selectedItemProperty().addListener { _, _, newValue ->
                        /*
                         * newValue is null when the map list is cleared when
                         * a new mod is loaded. Ideally this Tab object would
                         * be destroyed when closed and newValue would never be
                         * null, so just blame the JVM's rare use of The Reaper (GC)
                         *
                         * TODO:
                         *  - Remove listeners on tab close to avoid this issue?
                         *  - Check what happens when a map is deleted in the list
                         *    - What becomes the new selected item?
                         *      - If it just selects a new item, what happens when the list empties?
                         */
                        newValue?.let {
                            map.head.setMapname(index, it.baseFilename(GameData.mapExtension))
                            markChanged()
                        }
                    }
                }

                fields[i] = Field(Text("Mapname ${i + 1}").also { it.font = font }, cBox)
            }

            @Suppress("UNCHECKED_CAST")
            return fields as Array<Field>
        }

        fun initSpritesheet(): Field {
            val cBox = ComboBox(GameData.images)

            cBox.setCellFactory { _ ->
                object : ListCell<Path>() {
                    override fun updateItem(spritesheet: Path, empty: Boolean) {
                        super.updateItem(spritesheet, empty)
                        text = if (empty) null else spritesheet.baseFilename(GameData.imageExtension)
                    }
                }
            }

            val spritesheet = Paths.get(GameData.resourceFolder.toString() +
                                        File.separatorChar + GameData.imageFolder + File.separatorChar +
                                        map.head.spritesheetName + GameData.imageExtension)

            with(cBox.selectionModel) {
                select(spritesheet)
                selectedItemProperty().addListener { _, _, newValue ->
                    newValue?.let {
                        map.head.spritesheetName = it.baseFilename(GameData.imageExtension)
                        markChanged()
                        /*
                         * TODO:
                         * When I start pulling entity sprites directly from
                         * spritesheets, redraw entities in TileEditTab.
                         */
                    }
                }
            }

            return Field(Text("Spritesheet").also { it.font = font }, cBox)
        }

        fun initTilesets(): Array<Field> {
            val tilesetNames = map.head.tilesetNames

            val fields = arrayOfNulls<Field>(tilesetNames.size)

            for (i in tilesetNames.indices) {
                val cBox = ComboBox(GameData.images)

                cBox.setCellFactory { _ ->
                    object : ListCell<Path>() {
                        override fun updateItem(tileset: Path, empty: Boolean) {
                            super.updateItem(tileset, empty)
                            text = if (empty) null else tileset.baseFilename(GameData.imageExtension)
                        }
                    }
                }

                val tileset = Paths.get(GameData.resourceFolder.toString() +
                                        File.separatorChar + GameData.imageFolder + File.separator +
                                        tilesetNames[i] + GameData.imageExtension)

                val selectModel = cBox.selectionModel
                selectModel.select(tileset)
                val index = i
                selectModel.selectedItemProperty().addListener { _, _, newValue ->
                    newValue?.let {
                        map.head.setTilesetName(index, it.baseFilename(GameData.imageExtension))
                        markChanged()

                        //TODO: Only reload affected tileset, not all of them
                        tileEditTab.tilesetPane.loadTilesets.restart()
                        tileEditTab.tilesetPane.loadPxAttrs.restart()
                    }
                }

                fields[i] = Field(Text("Tileset ${i + 1}").also { it.font = font }, cBox)
            }

            @Suppress("UNCHECKED_CAST")
            return fields as Array<Field>
        }

        content = GridPane().apply {
            padding = Insets(10.0, 10.0, 10.0, 10.0)
            vgap = 10.0
            hgap = 20.0

            var y = 0

            initDescription().also { addRow(y++, it.label, it.field) }
            initMapnames().forEach { addRow(y++, it.label, it.field) }
            initSpritesheet().also { addRow(y++, it.label, it.field) }
            //TODO: Data
            initTilesets().forEach { addRow(y++, it.label, it.field) }
            //TODO: Visibility types
            //TODO: Scroll types
        }
    }

    override fun undo() = Unit

    override fun redo() = Unit

    override fun save() = Unit

    override fun markChanged() {
        super.markChanged()
        parent.markChanged()
    }

    override public fun markUnchanged() = super.markUnchanged()
}