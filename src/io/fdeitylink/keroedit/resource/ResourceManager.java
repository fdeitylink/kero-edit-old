package io.fdeitylink.keroedit.resource;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.InputStream;
import java.net.URISyntaxException;

import javafx.scene.image.Image;

public final class ResourceManager {
    private ResourceManager() {

    }

    public static Path getPath(final String filename) {
        try {
            return Paths.get(ResourceManager.class.getResource(filename).toURI());
        }
        catch (final URISyntaxException except) {
            return null;
        }
    }

    public static InputStream getInputStream(final String filename) {
        return ResourceManager.class.getResourceAsStream(filename);
    }

    public static Image getImage(final String filename) {
        return new Image("file:///" + filename, false);
    }
}