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

//TODO: Make the extension functions top-level functions?
object FXUtil {
    fun <V> task(callable: Callable<V>): Task<V> {
        return object : Task<V>() {
            override fun call() = callable.call()
        }
    }

    fun <V> service(callable: Callable<V>): Service<V> {
        return object : Service<V>() {
            override fun createTask(): Task<V> {
                return object : Task<V>() {
                    override fun call() = callable.call()
                }
            }
        }
    }

    /**
     * Returns a {@code String} representation of the given {@code Color}
     * suitable for use with {@code Color.web} or {@code Color.valueOf}.
     *
     * @param color the {@code Color} to get a {@code String} representation of
     *
     * @return a {@code String} representation of {@code color}
     */
    fun colorToString(color: Color): String {
        return String.format("0x%02X%02X%02X%02X",
                             (color.red * 255).toInt(),
                             (color.green * 255).toInt(),
                             (color.blue * 255).toInt(),
                             (color.opacity * 255).toInt())
    }

    /**
     * Creates and returns an {@code Alert} window
     *
     * @param type the {@code Alert.AlertType} of the alert. Defaults to {@code Alert.AlertType.NONE}
     * @param title the title text of the alert
     * @param headerText the header text of the alert. Defaults to null
     * @param message the content text of the alert
     *
     * @return the created {@code Alert}
     */
    fun createAlert(type: Alert.AlertType = Alert.AlertType.NONE, title: String?, headerText: String? = null,
                    message: String?): Alert {
        val alert = Alert(type)
        alert.title = title
        alert.headerText = headerText
        alert.contentText = message

        return alert
    }

    fun createDualTextFieldDialog(title: String?, headerText: String?, firstPrompt: String?, secondPrompt: String?):
            Dialog<Pair<String, String>> {
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
     * Creates and returns an {@code Alert} with an {@code TextBox} inside
     *
     * @param type the {@code Alert.AlertType} of the alert. Defaults to {@code Alert.AlertType.NONE}
     * @param title the title text of the alert
     * @param headerText the header text of the alert
     * @param message the content text of the alert
     * @param textAreaContent the content of the text box in the alert
     * @param editable true if the user should be able to edit the content of the text box, false otherwise
     *
     * @return the created {@code Alert}
     */
    fun createTextboxAlert(type: Alert.AlertType = Alert.AlertType.NONE, title: String?, headerText: String? = null,
                           message: String?, textAreaContent: String?, editable: Boolean = false): Alert {
        val alert = createAlert(type, title, headerText, message)

        val textArea = TextArea(textAreaContent)
        textArea.isEditable = editable
        textArea.isWrapText = true

        textArea.maxWidth = Double.MAX_VALUE
        textArea.maxHeight = Double.MAX_VALUE

        GridPane.setVgrow(textArea, Priority.ALWAYS)
        GridPane.setHgrow(textArea, Priority.ALWAYS)

        val pane = GridPane() //TODO: Other solution that doesn't use GridPane
        pane.maxWidth = Double.MAX_VALUE
        pane.add(textArea, 0, 0)

        alert.dialogPane.expandableContent = pane
        alert.dialogPane.isExpanded = true

        return alert
    }

    /**
     * Closes this tab such that if it has an {@code onCloseRequest} or
     * {@code onClosed EventHandler} set, it will be triggered.
     */
    fun Tab.close() {
        if (null == tabPane) {
            return
        }

        //https://stackoverflow.com/a/22783949/7355843
        //assumes default TabPane skin
        val behavior = (this.tabPane.skin as TabPaneSkin).behavior
        if (behavior.canCloseTab(this)) {
            behavior.closeTab(this)
        }
    }

    /**
     * Sets the maximum length of the text in this {@code TextInputControl}
     *
     * @param len the maximun length of the text
     *
     * @throws IllegalArgumentException if {@code len} is negative
     */
    fun TextInputControl.setMaxLen(len: Int) {
        require(len >= 0) { "Attempt to set max length of TextInputControl to negative value (len: $len)" }
        textProperty().addListener {
            _, _, newValue ->
            if (newValue.length > len) {
                text = newValue.substring(0, len)
            }
        }
    }

    /**
     * Sets the background of this {@code Region} to the given {@code Image}
     *
     * @param image the {@code Image} to use as the background of this {@code Region}
     */
    fun Region.setBackgroundImage(image: Image) {
        background = Background(BackgroundImage(image, null, null, null, null))
    }

    /**
     * Sets the background of this {@code Region} to the given {@code Color}
     *
     * @param color the {@code Color} to use as the background of this {@code Region}
     */
    fun Region.setBackgroundColor(color: Color) {
        background = Background(BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY))
    }

    /**
     * Returns a scaled version of this {@code Image}, or this {@code Image} if
     * this is null, the width or height of this is 0, or the given scale is 1
     *
     * @param scale the scale factor to scale this {@code Image} by
     *
     * @return this {@code Image} if this is null, the width or height of this is 0,
     * or {@code scale} is 1, otherwise a version of this scaled by {@code scale}
     */
    fun Image?.scaled(scale: Double): Image? {
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