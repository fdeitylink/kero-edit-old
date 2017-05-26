/*
 * TODO:
 * Cater to images in the localize folder (isInLocalize boolean?)
 * Use ReadOnlyObjectProperties to allow image editing and invalidation listeners
 */

package io.fdeitylink.keroedit.image

import java.io.File

import javafx.scene.image.Image
import javafx.scene.image.WritableImage

import io.fdeitylink.keroedit.gamedata.GameData

object ImageManager {
    private val imageMap = hashMapOf<String, Image>()

    private val EMPTY_IMAGE = Image("file:///")

    fun getImage(imageName: String, isTileset: Boolean): Image {
        if (!GameData.isInitialized) {
            throw IllegalStateException("GameData must be initialized before images can be retrieved from ImageManager")
        }

        if (imageMap.contains(imageName)) {
            return imageMap[imageName]!!
        }

        var image = Image("file:///" + GameData.resourceFolder.toString() +
                          File.separatorChar + GameData.imageFolder + File.separatorChar +
                          imageName + GameData.imageExtension, false)

        /*
         * TODO:
         * Detect if image is not square? (only for tilesets?)
         * Detect if size is smaller than 128px by 128px (is this size required?)
         */

        val width = image.width.toInt()
        val height = image.height.toInt()

        if (0 == width || 0 == height) {
            image = EMPTY_IMAGE
        }
        else if (isTileset && (TILESET_WIDTH < width || TILESET_HEIGHT < height)) {
            /*
             * If this is a tileset and it is larger than necessary, take only the
             * necessary portion. Tilesets are sometimes larger than necessary
             * (necessary being 128px by 128px)
             */
            image = WritableImage(image.pixelReader, 0, 0, TILESET_WIDTH, TILESET_HEIGHT)
        }

        imageMap.put(imageName, image)
        return image
    }

    fun wipe() = imageMap.clear()
}