package io.fdeitylink.keroedit.image;

import java.util.HashMap;

import java.io.File;

import io.fdeitylink.util.NullArgumentException;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import io.fdeitylink.keroedit.gamedata.GameData;

/*import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;*/

public final class ImageManager {
    //TODO: Cater to images in localize folder
    /**
     * Associates a filename (sans ".png") with an {@code Image}, which can
     * either be a tileset or spritesheet.
     */
    private static final HashMap <String, Image> imagesMap = new HashMap <>();
    /**
     * An {@code Image} with a width and height of that is put into
     * {@code imagesMap} when a loaded image has such dimensions
     * in order to reduce memory usage.
     */
    private static final Image EMPTY_IMAGE = new Image("file:///");

    private ImageManager() {

    }

    public static Image getImage(final String imageName, final boolean isTileset) {
        if (!GameData.INSTANCE.isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve image file when GameData has not been properly initialized yet");
        }

        NullArgumentException.requireNonNull(imageName, "getImage", "imageName");

        if (imagesMap.containsKey(imageName)) {
            return imagesMap.get(imageName);
        }
        Image image = new Image("file:///" + GameData.INSTANCE.getResourceFolder() +
                                File.separator + "img" + File.separatorChar +
                                imageName + ".png", false);

        //TODO: Detect if image is not square? (only for tilesets?)
        //TODO: Detect if size is smaller than 128px by 128px (is 128 x 128 required?)

        if (0 == image.getWidth() || 0 == image.getHeight()) {
            image = EMPTY_IMAGE;
        }
        else if (isTileset && //if this is a tileset and it is larger than necessary, take only relevant portion
                 (ImageDimension.TILESET_WIDTH < image.getWidth() || ImageDimension.TILESET_HEIGHT < image.getHeight())) {
            //crop down to useful portion (tilesets sometimes have more data than necessary for some reason)
            image = new WritableImage(image.getPixelReader(), 0, 0,
                                      ImageDimension.TILESET_WIDTH, ImageDimension.TILESET_HEIGHT);
        }

        imagesMap.put(imageName, image);
        return image;
    }

    /**
     * Clears {@code imagesMap} so no {@code Images} that have been
     * loaded in the past are accessible anymore. They will need to
     * be reloaded to make them available again.
     */
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