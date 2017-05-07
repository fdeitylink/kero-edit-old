package io.fdeitylink.keroedit.resource;

import java.util.HashMap;

import java.io.InputStream;

import java.net.URI;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.net.URISyntaxException;

import javafx.scene.image.Image;

import io.fdeitylink.keroedit.util.NullArgumentException;

public final class ResourceManager {
    private static FileSystem jarFS;
    private static final Image EMPTY_IMAGE = new Image("file:///");

    private ResourceManager() {

    }

    public static Path getPath(final String filename) {
        NullArgumentException.requireNonNull(filename, "getPath", "filename");
        try {
            final URI uri = ResourceManager.class.getResource(filename).toURI();
            try {
                return Paths.get(uri);
            }
            catch (final FileSystemNotFoundException except) { //for when distributed as a JAR
                try {
                    if (null != jarFS) {
                        return null;
                    }
                    //TODO: Does jarFS need to point to the root of the resource package?
                    //TODO: Close the filesystem at some point? - it needs to stay open...
                    //http://stackoverflow.com/a/25033217
                    final HashMap <String, String> env = new HashMap <>();
                    env.put("create", "true");
                    jarFS = FileSystems.newFileSystem(uri, env);
                    return Paths.get(uri);
                }
                catch (final IOException ex) {
                    return null;
                }
            }
        }
        catch (final URISyntaxException except) {
            return null;
        }
    }

    public static InputStream getInputStream(final String filename) {
        NullArgumentException.requireNonNull(filename, "getInputStream", "filename");
        //TODO: Do I need to use getPath() here as it does the whole jarFS thing?
        return ResourceManager.class.getResourceAsStream(filename);
    }

    public static Image getImage(final String filename) {
        //TODO: Return empty image if filename is null?
        NullArgumentException.requireNonNull(filename, "getImage", "filename");

        final Path p = getPath(filename);
        if (null == p) {
            return EMPTY_IMAGE;
        }
        return new Image(p.toUri().toString(), false);
    }
}