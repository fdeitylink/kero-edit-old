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

    private ImageManager() {

    }

    public static Image getImage(final String imageName, final boolean isTileset) {
        //TODO: Throw IllegalStateException if GameData is not yet valid
        if (imagesMap.containsKey(imageName)) {
            return imagesMap.get(imageName);
        }
        Image image = new Image("file:///" + GameData.getResourceFolder().toAbsolutePath().toString() +
                                File.separator + "img" + File.separatorChar +
                                imageName + ".png", false);

        //if this is a tileset and it is larger than necessary, take only relevant portion
        if (isTileset && 0 < image.getWidth() &&
            (ImageDimensions.TILESET_WIDTH < image.getWidth() ||
             ImageDimensions.TILESET_HEIGHT < image.getHeight())) {
            //crop down to useful portion (tilesets sometimes have more data than necessary for some reason)
            image = new WritableImage(image.getPixelReader(), 0, 0,
                                      ImageDimensions.TILESET_WIDTH, ImageDimensions.TILESET_HEIGHT);
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