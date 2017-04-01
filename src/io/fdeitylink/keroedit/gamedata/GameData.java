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

import io.fdeitylink.keroedit.Messages;

//TODO: Throws errors (or mkdirs()?) if rsc_x is missing necessary folders

/**
 * Singleton class for storing about a Kero Blaster game
 */
public final class GameData {
    private static GameData inst;

    private MOD_TYPE modType;

    private Path executable;
    private Path resourceFolder;

    private ArrayList <String> bgms;
    private ArrayList <String> maps;
    private ArrayList <String> images;
    private ArrayList <String> soundEffects;
    private ArrayList <String> scripts;

    private GameData() {

    }

    /**
     * Initializes the singleton GameData object based on the given executable
     *
     * @param executable {@code Path} pointing to the Kero Blaster executable
     *
     * @throws NoSuchFileException If the given executable is null
     */
    public static void init(final Path executable) throws IOException {
        //TODO: Move all/most of this stuff into constructor?

        inst = new GameData();

        if (null == executable) {
            throw new NullPointerException(Messages.getString("GameData.EXECUTABLE_NULL"));
        }
        else if (!Files.exists(executable)) {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NONEXISTENT"),
                                                               executable.toAbsolutePath()));
        }
        else if (!executable.getFileName().toString().endsWith(".exe")) {
            throw new IllegalArgumentException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NOT_EXE"),
                                                                    executable.toAbsolutePath()));
        }
        inst.executable = executable;

        final DirectoryStream <Path> dirPaths;
        try {
            dirPaths = Files.newDirectoryStream(executable.getParent(), entry -> Files.isDirectory(entry));
        }
        catch (final IOException except) {
            inst = null;
            throw new IOException(MessageFormat.format(Messages.getString("GameData.ListFilesIOExcept.MESSAGE"),
                                                       executable.toAbsolutePath()), except);
        }

        boolean rscExists = false;
        for (final Path p : dirPaths) {
            if (p.endsWith("rsc_p")) {
                inst.resourceFolder = Paths.get(executable.getParent().toAbsolutePath().toString() +
                                                File.separatorChar + "rsc_p");
                inst.modType = MOD_TYPE.PINK_HOUR; //TODO: Detect or ask if pink heaven
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

    public static Path getExecutable() {
        if (null == inst) {
            throw new IllegalStateException("GameData has not been properly initialized yet");
        }
        return inst.executable;
    }

    public static Path getResourceFolder() {
        if (null == inst) {
            throw new IllegalStateException("GameData has not been properly initialized yet");
        }
        return inst.resourceFolder;
    }

    public static MOD_TYPE getModType() {
        if (null == inst) {
            throw new IllegalStateException("GameData has not been properly initialized yet");
        }
        return inst.modType;
    }

    public static ArrayList <String> getMapList() {
        if (null == inst) {
            throw new IllegalStateException("GameData has not been properly initialized yet");
        }
        return new ArrayList <>(inst.maps);
    }

    public static void removeMap(final String mapname) {
        if (null == inst) {
            throw new IllegalStateException("GameData has not been properly initialized yet");
        }
        if (!inst.maps.contains(mapname)) {
            throw new IllegalArgumentException("No such map (" + mapname + ") exists");
        }
        inst.maps.remove(mapname);
        //TODO: actually delete map
    }

    private static ArrayList <String> getFileList(final String pathFromResource, final String extension) throws IOException {
        final Path basePath = Paths.get(inst.resourceFolder.toAbsolutePath().toString() +
                                        File.separatorChar + pathFromResource);

        final ArrayList <String> nameList = new ArrayList <>();
        try {
            final DirectoryStream <Path> pathList = Files.newDirectoryStream(basePath,
                                                                             entry -> entry.toString().endsWith(extension));
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