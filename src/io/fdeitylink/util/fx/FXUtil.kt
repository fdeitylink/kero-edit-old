/*
 * TODO:
 * Make the extension functions top-level functions?
 * Make all of the functions top-level functions?
 */

package io.fdeitylink.util.fx

import javafx.util.Callback

import java.util.concurrent.Callable

import javafx.scene.layout.Region
import javafx.scene.layout.GridPane

import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundImage
import javafx.scene.layout.BackgroundFill

import javafx.scene.layout.CornerRadii
import javafx.scene.layout.Priority
import javafx.geometry.Insets

import javafx.scene.control.Tab
import com.sun.javafx.scene.control.skin.TabPaneSkin
import javafx.application.Platform

import javafx.scene.control.TextInputControl
import javafx.scene.control.TextArea
import javafx.scene.control.TextField

import javafx.scene.control.Dialog
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image

import java.awt.image.BufferedImage
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp

import javafx.scene.paint.Color

import javafx.concurrent.Service
import javafx.concurrent.Task

object FXUtil {
    /**
     * Returns a [Task] that simply executes the given [Callable] in
     * its [Task.call] method
     *
     * @param callable the [Callable] object to invoke [Callable.call]
     * on in the body of the [Task.call] method for the returned [Task]
     *
     * @return a [Task] that calls [Callable.call] on [callable] in its
     * [Task.call] method
     */
    fun <V> task(callable: Callable<V>): Task<V> {
        //TODO: Take a () -> V
        return object: Task<V>() {
            override fun call() = callable.call()
        }
    }

    /**
     * Returns a [Service] that in its [Service.createTask] method
     * returns a [Task] that executes the given [Callable] in its
     * [Task.call] method
     *
     * @param callable the [Callable] object to invoke [Callable.call]
     * on in the body of the [Task.call] method for the [Task] that is
     * returned in the [Service.createTask] method of the returned [Service]
     *
     * @return a [Service] that, in its [Service.createTask] method, returns
     * a [Task] that calls [Callable.call] on [callable] in its [Task.call] method
     */
    fun <V> service(callable: Callable<V>): Service<V> {
        //TODO: Take a () -> V
        return object: Service<V>() {
            override fun createTask(): Task<V> {
                return object: Task<V>() {
                    override fun call() = callable.call()
                }
            }
        }
    }

    /**
     * Returns a [String] representation of the given [Color]
     * suitable for use with [Color.web] and [Color.valueOf].
     * The official documentation for [Color.toString] does
     * not specify that it can be used for the methods mentioned
     * above, so this method should be used for such purposes as
     * it will always be compatible.
     *
     * @param color the [Color] to get a [String] representation of
     *
     * @return a [String] representation of [color]
     */
    fun colorToString(color: Color): String {
        return String.format("0x%02X%02X%02X%02X",
                             (color.red * 255).toInt(),
                             (color.green * 255).toInt(),
                             (color.blue * 255).toInt(),
                             (color.opacity * 255).toInt())
    }

    /**
     * Creates and returns an [Alert] with the given properties
     *
     * @param type the [Alert.AlertType] of the [Alert]. Defaults to [Alert.AlertType.NONE]
     * @param title the title text of the [Alert]. Defaults to null
     * @param headerText the header text of the [Alert]. Defaults to null
     * @param message the content text of the [Alert]. Defaults to null
     *
     * @return the created [Alert]
     */
    fun createAlert(type: Alert.AlertType = Alert.AlertType.NONE, title: String? = null, headerText: String? = null,
                    message: String? = null): Alert {
        val alert = Alert(type)
        alert.title = title
        alert.headerText = headerText
        alert.contentText = message

        return alert
    }

    /**
     * Creates and returns an [Dialog] with the given properties and two [TextField]s
     *
     * @param title the title text of the [Dialog]. Defaults to null
     * @param headerText the header text of the [Dialog]. Defaults to null
     * @param firstPrompt the prompt text for the first [TextField]. Defaults to null
     * @param secondPrompt the prompt text for the second [TextField]. Defaults to null
     *
     * @return the created [Dialog]. Its result is a [Pair] with both components being
     * [String]s. The first component is the text of the first [TextField], and the second
     * component is the text of the second [TextField], **provided that the user confirmed
     * their choice by clicking the OK button**. If they did not, such as by clicking the
     * Cancel button, then both [String]s in the [Pair] result will be empty.
     */
    fun createDualTextFieldDialog(title: String? = null, headerText: String? = null,
                                  firstPrompt: String? = null, secondPrompt: String? = null
                                 ): Dialog<Pair<String, String>> {
        //TODO: Add 'message' parameter that defaults to null?

        val dialog = Dialog<Pair <String, String>>()
        dialog.title = title
        dialog.headerText = headerText

        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        val firstField = TextField()
        firstField.promptText = firstPrompt

        val secondField = TextField()
        secondField.promptText = secondPrompt

        val pane = GridPane()
        pane.add(firstField, 1, 0)
        pane.add(secondField, 1, 1)

        dialog.dialogPane.content = pane

        Platform.runLater(firstField::requestFocus)

        dialog.resultConverter = Callback {
            if (ButtonType.OK == it) {
                return@Callback Pair<String, String>(firstField.text, secondField.text)
            }
            return@Callback Pair("", "")
        }

        return dialog
    }

    /**
     * Creates and returns an [Alert] with the given properties and a [TextArea] inside
     *
     * @param type the [Alert.AlertType] of the alert. Defaults to [Alert.AlertType.NONE]
     * @param title the title text of the [Alert]. Defaults to null
     * @param headerText the header text of the [Alert]. Defaults to null
     * @param message the content text of the [Alert]. Defaults to null
     * @param textAreaContent the content of the text box in the [Alert]. Defaults to null
     * @param editable true if the user should be able to edit the content of the [TextArea], false otherwise.
     * Defaults to false.
     *
     * @return the created [Alert]
     */
    fun createTextboxAlert(type: Alert.AlertType = Alert.AlertType.NONE, title: String? = null,
                           headerText: String? = null, message: String? = null, textAreaContent: String? = null,
                           editable: Boolean = false): Alert {
        val alert = createAlert(type, title, headerText, message)

        val textArea = TextArea(textAreaContent)
        textArea.isEditable = editable
        textArea.isWrapText = true

        textArea.maxWidth = Double.MAX_VALUE
        textArea.maxHeight = Double.MAX_VALUE

        GridPane.setVgrow(textArea, Priority.ALWAYS)
        GridPane.setHgrow(textArea, Priority.ALWAYS)

        val pane = GridPane() //TODO: Find another solution that doesn't use a GridPane?
        pane.maxWidth = Double.MAX_VALUE
        pane.add(textArea, 0, 0)

        alert.dialogPane.expandableContent = pane
        alert.dialogPane.isExpanded = true

        return alert
    }

    //TODO: Test this method on a tab that is specified as not-closeable
    /**
     * Closes the receiving [Tab] such that if it has an [javafx.event.EventHandler]
     * set for its onCloseRequest or onClosed property, they will be triggered.
     *
     * @receiver a [Tab]
     */
    fun Tab.close() {
        if (null == tabPane) {
            return
        }

        /*
         * Assumes default TabPane skin
         * https://stackoverflow.com/a/22783949/7355843
         */
        val behavior = (this.tabPane.skin as TabPaneSkin).behavior
        if (behavior.canCloseTab(this)) {
            behavior.closeTab(this)
        }
    }

    /**
     * Sets the maximum length of the [String] text in the receiving [TextInputControl]
     *
     * @receiver a [TextInputControl] whose text should have a maximum length
     *
     * @param len the maximum length of the text
     *
     * @throws IllegalArgumentException if [len] is negative
     */
    fun TextInputControl.setMaxLen(len: Int) {
        //TODO: Remove previous listeners set by this method
        require(len >= 0) { "Attempt to set max length of TextInputControl to a negative value (len: $len)" }
        textProperty().addListener { _, _, newValue ->
            if (newValue.length > len) {
                text = newValue.substring(0, len)
            }
        }
    }

    /**
     * Sets the background of the receiving [Region] to the given [Image]
     *
     * @receiver a [Region] to set the background image of
     *
     * @param image the [Image] to use as the background of the receiving [Region]
     */
    fun Region.setBackgroundImage(image: Image) {
        background = Background(BackgroundImage(image, null, null, null, null))
    }

    /**
     * Sets the background of the receiving [Region] to the given [Color]
     *
     * @receiver a [Region] to set the background color of
     *
     * @param color the [Color] to use as the background of the receiving [Region]
     */
    fun Region.setBackgroundColor(color: Color) {
        background = Background(BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY))
    }

    /**
     * Returns a scaled version of the receiving [Image], or this [Image]
     * if this [Image] is null, the width or height of this [Image] is 0,
     * or [scale] is 1.0
     *
     * @receiver an [Image] to scale by [scale]
     *
     * @param scale the scale factor to scale this [Image] by
     *
     * @return the receiving [Image] if it is null, the width or height of it
     * is 0, or [scale] is 1.0, otherwise a version of it scaled by [scale]
     */
    fun Image?.scale(scale: Double): Image? {
        if (null == this || 1.0 == scale) {
            return this
        }

        val srcWidth = width.toInt()
        val srcHeight = height.toInt()

        if (0 == srcWidth || 0 == srcHeight) {
            return this
        }

        val dest = BufferedImage((srcWidth * scale).toInt(), (srcHeight * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val src = SwingFXUtils.fromFXImage(this, null)

        val trans = AffineTransform()
        trans.scale(scale, scale)
        AffineTransformOp(trans, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(src, dest)

        return SwingFXUtils.toFXImage(dest, null)
    }
}