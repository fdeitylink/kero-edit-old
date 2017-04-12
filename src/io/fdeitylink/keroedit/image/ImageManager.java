package io.fdeitylink.keroedit.image;

import java.util.HashMap;

import java.io.File;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import io.fdeitylink.keroedit.gamedata.GameData;

/*import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;*/

public final class ImageManager {
    private static final HashMap <String, Image> imagesMap = new HashMap <>();
    private static final Image emptyImage = new Image("file:///");

    private ImageManager() {

    }

    public static Image getImage(final String imageName, final boolean isTileset) {
        if (!GameData.isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve image file when GameData has not been properly initialized yet");
        }

        if (imagesMap.containsKey(imageName)) {
            return imagesMap.get(imageName);
        }
        Image image = new Image("file:///" + GameData.getResourceFolder().toAbsolutePath().toString() +
                                File.separator + "img" + File.separatorChar +
                                imageName + ".png", false);

        //TODO: Detect if image is not square? (only for tilesets?)

        if (0 == image.getWidth() || 0 == image.getHeight()) {
            image = emptyImage;
        }
        //if this is a tileset and it is larger than necessary, take only relevant portion
        //if code in this block runs, image is definitely larger than 0x0
        if (isTileset &&
            (ImageDimension.TILESET_WIDTH < image.getWidth() ||
             ImageDimension.TILESET_HEIGHT < image.getHeight())) {
            //crop down to useful portion (tilesets sometimes have more data than necessary for some reason)
            image = new WritableImage(image.getPixelReader(), 0, 0,
                                      ImageDimension.TILESET_WIDTH, ImageDimension.TILESET_HEIGHT);
        }

        imagesMap.put(imageName, image);
        return image;
    }

    public static void wipe() {
        imagesMap.clear();
    }

    //If I allow tileset/image editing, I'll use these and the setup will be similar to PxAttrManager
    /*public static ReadOnlyObjectProperty <Image> getImage(final String imageName) {

    }

    private static class ReadOnlyImageWrapper extends ReadOnlyObjectWrapper <Image> {
        ReadOnlyImageWrapper(final Image image) {
            super(image);
        }
    }*/
}