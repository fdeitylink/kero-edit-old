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

import io.fdeitylink.keroedit.util.NullArgumentException;

import io.fdeitylink.keroedit.Messages;

/**
 * Singleton class for storing about a Kero Blaster game
 */

public final class GameData {
    private static GameData inst;

    private MOD_TYPE modType;

    private Path executable;
    private Path resourceFolder;

    private ArrayList <String> bgms;

    //TODO: if PxPack is renamed, it must also be renamed here
    private ArrayList <String> maps;

    private ArrayList <String> images;
    private ArrayList <String> soundEffects;
    private ArrayList <String> scripts;

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
        //TODO: Throws errors (or mkdirs()?) if rsc_x is missing necessary subfolders
        inst = new GameData();

        if (null == executable) {
            throw new NullArgumentException("init", "executable");
        }
        if (!executable.getFileName().toString().endsWith(".exe")) {
            throw new IllegalArgumentException("Attempt to initialize GameData with file " +
                                               executable.toAbsolutePath() + " that doesn't end with \".exe\"");
        }
        if (!Files.exists(executable)) {
            //TODO: Throw IllegalArgumentException?
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NONEXISTENT"),
                                                               executable.toAbsolutePath()));
        }
        inst.executable = executable;

        boolean rscExists = false;
        try (DirectoryStream <Path> dirPaths = Files.newDirectoryStream(executable.getParent(),
                                                                        entry -> Files.isDirectory(entry))) {
            for (final Path p : dirPaths) {
                if (p.endsWith("rsc_p")) {
                    inst.resourceFolder = Paths.get(executable.getParent().toAbsolutePath().toString() +
                                                    File.separatorChar + "rsc_p");
                    inst.modType = MOD_TYPE.PINK_HOUR; //TODO: Detect or ask if Pink Heaven
                    rscExists = true;
                    break;
                }
                else if (p.endsWith("rsc_k")) {
                    inst.resourceFolder = Paths.get(executable.getParent().toAbsolutePath().toString() +
                                                    File.separatorChar + "rsc_k");
                    inst.modType = MOD_TYPE.KERO_BLASTER;
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
            inst.bgms = getFileList(File.separatorChar + "bgm" + File.separatorChar, ".ptcop");

            inst.maps = getFileList(File.separatorChar + "field" + File.separatorChar, ".pxpack");

            inst.soundEffects = getFileList(File.separatorChar + "se" + File.separatorChar, ".ptnoise");

            inst.scripts = getFileList(File.separatorChar + "text" + File.separatorChar, ".pxeve");
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

    public static ArrayList <String> getMapList() {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        return new ArrayList <>(inst.maps);
    }

    public static void removeMap(final String mapName) {
        if (!isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve information from GameData " +
                                            "when it has not been properly initialized yet");
        }
        if (!inst.maps.contains(mapName)) {
            throw new IllegalArgumentException("Attempt to remove map that doesn't exist " +
                                               "(mapName: " + mapName + ")");
        }
        inst.maps.remove(mapName);
        //TODO: actually delete map file
    }

    private static ArrayList <String> getFileList(final String pathFromResource, final String extension)
            throws IOException {
        final Path basePath = Paths.get(inst.resourceFolder.toAbsolutePath().toString() +
                                        File.separatorChar + pathFromResource);

        final ArrayList <String> nameList = new ArrayList <>();
        try {
            final DirectoryStream <Path> pathList =
                    Files.newDirectoryStream(basePath, entry -> entry.toString().endsWith(extension));
            for (final Path p : pathList) {
                final String filename = p.getFileName().toString();
                nameList.add(filename.substring(0, filename.lastIndexOf(extension)));
            }
        }
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"),
                                                       inst.executable.toAbsolutePath()), except);
        }

        return nameList;
    }

    public enum MOD_TYPE {
        PINK_HOUR,
        PINK_HEAVEN,
        KERO_BLASTER
    }
}