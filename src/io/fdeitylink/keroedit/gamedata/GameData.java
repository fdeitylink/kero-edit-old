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

import io.fdeitylink.keroedit.Messages;

/**
 * Singleton class for storing about a Kero Blaster mod
 */
public final class GameData {
    //TODO: private static final Strings for folder names?
    //TODO: Filter out files with names that are too long and that contain spaces

    private static GameData inst;

    private MOD_TYPE modType;

    private Path executable;
    private Path resourceFolder;

    //TODO: Make lists of Paths rather than Strings?

    private ObservableList <String> bgms;

    //TODO: If PxPack is renamed, it must also be renamed here
    //TODO: Add ChangeListener that deletes map file when a map is removed from this list
    private ObservableList <String> maps;

    private ObservableList <String> images;

    private ObservableList <String> soundFX;

    private ObservableList <String> scripts;

    private GameData() {

    }

    /**
     * Initializes the singleton GameData object based on the given executable
     *
     * @param executable A {@code Path} that references the executable for a mod
     *
     * @throws NoSuchFileException If the given executable is null
     */
    public static void init(final Path executable) throws IOException {
        //TODO: Move all/most of this stuff into constructor?
        //TODO: Throws errors (or mkdirs()?) if rsc_x is missing necessary subfolders?
        NullArgumentException.requireNonNull(executable, "init", "executable");

        if (!executable.getFileName().toString().endsWith(".exe")) {
            throw new IllegalArgumentException("Attempt to initialize GameData with file " +
                                               executable.toAbsolutePath() + " that doesn't end with \".exe\"");
        }
        if (!Files.exists(executable)) {
            //TODO: Throw IllegalArgumentException?
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NONEXISTENT"),
                                                               executable.toAbsolutePath()));
        }

        inst = new GameData();
        inst.executable = executable.toAbsolutePath();

        boolean rscExists = false;
        //TODO: Use path matching? - "/*/"
        try (DirectoryStream <Path> dirPaths = Files.newDirectoryStream(executable.getParent(),
                                                                        entry -> Files.isDirectory(entry))) {
            for (final Path p : dirPaths) {
                if (p.endsWith("rsc_k")) {
                    inst.resourceFolder = p.toAbsolutePath();
                    inst.modType = MOD_TYPE.KERO_BLASTER;
                    rscExists = true;
                    break;
                }
                else if (p.endsWith("rsc_p")) {
                    inst.resourceFolder = p.toAbsolutePath();
                    inst.modType = MOD_TYPE.PINK_HOUR; //TODO: Detect or ask if Pink Heaven
                    rscExists = true;
                    break;
                }
            }
        }
        catch (final IOException except) {
            inst = null;
            throw new IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"),
                                                       executable.toAbsolutePath()), except);
        }

        if (!rscExists) {
            inst = null;
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.MISSING_RSC"),
                                                               executable.toAbsolutePath()));
        }

        try {
            inst.bgms = getFileList("bgm", ".ptcop");

            inst.maps = getFileList("field", ".pxpack");
            /*inst.maps.addListener((final ListChangeListener.Change <? extends String> c) -> {
                //TODO: Should I go back to returning immutable ObservableLists in getX() and provide removeX() methods?
                for (final String fname : c.getRemoved()) {
                    try {
                        Files.deleteIfExists(Paths.get(inst.resourceFolder.toString() +
                                                       File.separator + "field" + File.separatorChar +
                                                       fname + ".pxpack"));
                    }
                    catch (final IOException except) {
                        //TODO: Something...
                    }
                }
            });*/

            //TODO: Separate image lists (tilesets, spritesheets, etc.)
            inst.images = getFileList("img", ".png");

            inst.soundFX = getFileList("se", ".ptnoise");

            inst.scripts = getFileList("text", ".pxeve");
        }
        catch (final IOException except) {
            inst = null;
            throw except;
        }
    }

    public static boolean isInitialized() {
        return null != inst;
    }

    public static void wipe() {
        inst = null;
    }

    public static Path getExecutable() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.executable;
    }

    public static Path getResourceFolder() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.resourceFolder;
    }

    public static MOD_TYPE getModType() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.modType;
    }

    public static ObservableList <String> getBgmList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.bgms;
    }

    public static ObservableList <String> getMapList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.maps;
    }

    public static ObservableList <String> getImageList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.images;
    }

    public static ObservableList <String> getSoundFXList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.soundFX;
    }

    public static ObservableList <String> getScriptList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return inst.scripts;
    }

    private static ObservableList <String> getFileList(final String pathFromRsc, final String ext)
            throws IOException {
        //TODO: Omit '.' from ext in calls in init(), put '.' in glob in DirectoryStream below
        final Path basePath = Paths.get(inst.resourceFolder.toAbsolutePath().toString() +
                                        File.separatorChar + pathFromRsc);

        final ObservableList <String> nameList = FXCollections.observableList(new ArrayList <>());
        try (final DirectoryStream <Path> pathList = Files.newDirectoryStream(basePath, '*' + ext)) {
            for (final Path p : pathList) {
                final String fname = p.getFileName().toString();
                nameList.add(fname.substring(0, fname.lastIndexOf(ext)));
            }
        }
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"),
                                                       inst.executable), except);
        }

        return nameList;
    }

    public enum MOD_TYPE {
        PINK_HOUR,
        PINK_HEAVEN,
        KERO_BLASTER
    }
}