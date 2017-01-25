package com.fdl.keroedit.resource;

import java.io.File;
import java.io.InputStream;

import javafx.scene.image.Image;

public class ResourceManager {
    public static File getFile(final String filename) {
        return new File(ResourceManager.class.getResource(filename).toString());
    }

    public static InputStream getFileAsInputStream(final String filename) {
        return ResourceManager.class.getResourceAsStream(filename);
    }

    public static Image getImage(final String filename) {
        return new Image(getFileAsInputStream(filename));
    }
}