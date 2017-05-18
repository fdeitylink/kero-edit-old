package io.fdeitylink.keroedit.gamedata;

import java.util.ArrayList;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.nio.file.DirectoryStream;

import java.io.IOException;

import java.nio.file.NoSuchFileException;

import java.text.MessageFormat;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import io.fdeitylink.util.NullArgumentException;

import io.fdeitylink.util.UtilsKt;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.map.PxPack;

/**
 * Singleton class for storing info about a Kero Blaster mod
 */
public enum GameData {
    INSTANCE;

    public static final String bgmFolder = "bgm";
    public static final String bgmExtension = ".ptcop";

    public static final String mapFolder = "field";
    public static final String mapExtension = ".pxpack";

    public static final String imageFolder = "img";
    public static final String imageExtension = ".png";

    public static final String sfxFolder = "se";
    public static final String sfxExtension = ".ptnoise";

    public static final String scriptFolder = "text";
    public static final String scriptExtension = ".pxeve";

    private boolean initialized = false;

    private MOD_TYPE modType;

    private Path executable;
    private Path resourceFolder;

    //TODO: Make lists of Paths rather than Strings?

    private ObservableList <Path> bgms;

    //TODO: If PxPack is renamed, it must also be renamed here
    //TODO: Add ChangeListener that deletes map file when a map is removed from this list
    private ObservableList <Path> maps;

    private ObservableList <Path> images;

    private ObservableList <Path> soundFX;

    private ObservableList <Path> scripts;

    GameData() {

    }

    /**
     * Initializes the singleton GameData object based on the given executable
     *
     * @param executable A {@code Path} that references the executable for a mod
     *
     * @throws NoSuchFileException If the given executable is null
     */
    public void init(final Path executable) throws IOException {
        wipe();

        //TODO: Throws errors (or mkdirs()?) if rsc_x is missing necessary subfolders?
        NullArgumentException.Companion.requireNonNull(executable, "init", "executable");

        if (!executable.getFileName().toString().endsWith(".exe")) {
            throw new IllegalArgumentException("Attempt to initialize GameData with file " +
                                               executable.toAbsolutePath() + " that doesn't end with \".exe\"");
        }
        if (!Files.exists(executable)) {
            //TODO: Throw IllegalArgumentException?
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NONEXISTENT"),
                                                               executable.toAbsolutePath()));
        }

        this.executable = executable.toAbsolutePath();

        boolean rscExists = false;
        //TODO: Use path matching? - "/*/"
        try (DirectoryStream <Path> dirPaths = Files.newDirectoryStream(executable.getParent(),
                                                                        entry -> Files.isDirectory(entry))) {
            for (final Path p : dirPaths) {
                if (p.endsWith("rsc_k")) {
                    resourceFolder = p.toAbsolutePath();
                    modType = MOD_TYPE.KERO_BLASTER;
                    rscExists = true;
                    break;
                }
                else if (p.endsWith("rsc_p")) {
                    resourceFolder = p.toAbsolutePath();
                    modType = MOD_TYPE.PINK_HOUR; //TODO: Detect or ask if Pink Heaven
                    rscExists = true;
                    break;
                }
            }
        }
        catch (final IOException except) {
            wipe();
            throw new IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"),
                                                       executable.toAbsolutePath()), except);
        }

        if (!rscExists) {
            wipe();
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.MISSING_RSC"),
                                                               executable.toAbsolutePath()));
        }

        try {
            bgms = getFileList(bgmFolder, bgmExtension);

            maps = getFileList(mapFolder, mapExtension);
            /*maps.addListener((final ListChangeListener.Change <? extends String> c) -> {
                //TODO: Should I go back to returning immutable ObservableLists in getX() and provide removeX() methods?
                for (final String fname : c.getRemoved()) {
                    try {
                        Files.deleteIfExists(Paths.get(INSTANCE.resourceFolder.toString() +
                                                       File.separator + mapFolder + File.separatorChar +
                                                       fname + mapExtension));
                    }
                    catch (final IOException except) {
                        //TODO: Something...
                    }
                }
            });*/

            //TODO: Separate image lists (tilesets, spritesheets, etc.)
            images = getFileList(imageFolder, imageExtension);

            soundFX = getFileList(sfxFolder, sfxExtension);

            scripts = getFileList(scriptFolder, scriptExtension);
        }
        catch (final IOException except) {
            wipe();
            throw except;
        }

        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void wipe() {
        initialized = false;

        modType = null;

        executable = null;
        resourceFolder = null;

        bgms = null;
        maps = null;
        images = null;
        soundFX = null;
        scripts = null;
    }

    public Path getExecutable() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return executable;
    }

    public Path getResourceFolder() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return resourceFolder;
    }

    public MOD_TYPE getModType() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return modType;
    }

    public ObservableList <Path> getBgmList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return bgms;
    }

    public ObservableList <Path> getMapList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return maps;
    }

    public ObservableList <Path> getImageList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return images;
    }

    public ObservableList <Path> getSoundFXList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return soundFX;
    }

    public ObservableList <Path> getScriptList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return scripts;
    }

    private ObservableList <Path> getFileList(final String pathFromRsc, final String ext)
            throws IOException {
        final Path basePath = Paths.get(resourceFolder.toAbsolutePath().toString() + File.separatorChar + pathFromRsc);

        final ObservableList <Path> list = FXCollections.observableList(new ArrayList <>());
        try (final DirectoryStream <Path> paths = Files.newDirectoryStream(basePath, '*' + ext)) {
            for (final Path p : paths) {
                final String fname = UtilsKt.baseFilename(p, ext);
                if (fname.length() <= PxPack.Head.FILENAME_MAX_LEN && !fname.contains(" ")) {
                    list.add(p);
                }
            }
        }
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"),
                                                       executable), except);
        }

        return list;
    }

    public enum MOD_TYPE {
        PINK_HOUR,
        PINK_HEAVEN,
        KERO_BLASTER
    }
}