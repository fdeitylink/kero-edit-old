package com.fdl.keroedit.resource;

import java.io.File;
import java.io.InputStream;

import javafx.scene.image.Image;

public class ResourceManager {
    public static File getFile(final String filename) {
        //TODO: Have the file have a normal path rather than url-based
        final String path = ResourceManager.class.getResource(filename).toString();
        return new File(path.replaceFirst("file:/", ""));
    }

    public static InputStream getFileAsInputStream(final String filename) {
        return ResourceManager.class.getResourceAsStream(filename);
    }

    public static Image getImage(final String filename) {
        final Image result = new Image(getFileAsInputStream(filename));
        while (1 != result.getProgress()) {

        }
        return result;
    }
}