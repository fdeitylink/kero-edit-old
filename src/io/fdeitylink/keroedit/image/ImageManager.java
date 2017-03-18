package io.fdeitylink.keroedit.image;

import java.io.File;
import java.util.HashMap;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import io.fdeitylink.keroedit.gamedata.GameData;

/*import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;*/

public class ImageManager {
    private static final HashMap <String, Image> imagesMap = new HashMap <>();

    private ImageManager() {

    }

    public static Image getImage(final String imageName, final boolean tileset) {
        if (imagesMap.containsKey(imageName)) {
            return imagesMap.get(imageName);
        }
        Image image = new Image("file:///" + GameData.getResourceFolder().toAbsolutePath().toString() +
                                File.separator + "img" + File.separatorChar +
                                imageName + ".png", false);

        //if this is a tileset and it is larger than necessary, take only relevant portion
        if (tileset && 0 < image.getWidth() &&
            (Integer.compare(ImageDimensions.TILESET_WIDTH, (int)image.getWidth()) < 0 ||
             Integer.compare(ImageDimensions.TILESET_HEIGHT, (int)image.getHeight())< 0)) {
            //crop down to useful portion (tilesets sometimes have more data than necessary for some reason
            image = new WritableImage(image.getPixelReader(), 0, 0,
                                      ImageDimensions.TILESET_WIDTH, ImageDimensions.TILESET_HEIGHT);
        }

        imagesMap.put(imageName, image);
        return image;
    }

    //In the event that I allow tileset/image editing, I'll use these and the setup will be similar to
    /*public static ReadOnlyObjectProperty <Image> getImage(final String imageName) {

    }*/

    /*private static class ReadOnlyImageWrapper extends ReadOnlyObjectWrapper <Image> {
        ReadOnlyImageWrapper(final Image image) {
            super(image);
        }
    }*/
}