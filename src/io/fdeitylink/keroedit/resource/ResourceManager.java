package io.fdeitylink.keroedit.resource;

import java.io.File;
import java.io.InputStream;

import javafx.scene.image.Image;

public class ResourceManager {
    public static File getFile(final String filename) {
        //replaceFirst() should turn path into a normal file path rather than URL/URI/etc.
        return new File(ResourceManager.class.getResource(filename).toString().replaceFirst("file:/", ""));
    }

    public static InputStream getInputStream(final String filename) {
        return ResourceManager.class.getResourceAsStream(filename);
    }

    public static Image getImage(final String filename) {
        return new Image(ResourceManager.class.getResource(filename).toString(), false);
    }
}