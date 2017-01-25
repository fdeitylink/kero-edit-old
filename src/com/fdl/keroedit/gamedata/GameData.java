package com.fdl.keroedit.gamedata;

import com.fdl.keroedit.Messages;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileFilter;

import java.nio.file.NoSuchFileException;

//TODO: Throws errors (or mkdirs()?) if rsc_x is missing necessary folders
//TODO: Store files in Sets and reimplement equals() for PxPack?

/**
 * Singleton class for storing about a Kero Blaster game
 */
public class GameData {
    private static GameData gameData;

    private File executable;
    private File resourceFolder;

    private ArrayList <File> bgms;
    private ArrayList <File> maps; //TODO: Make arraylist of PxPack objects
    //TODO: Images (tilesets, spritesheets, and more!)
    private ArrayList <File> soundEffects;
    private ArrayList <File> scripts; //TODO: Make arraylist of PxEve objects

    private GameData() {

    }

    /**
     * Initializes the singleton GameData object based on the given executable
     *
     * @param executable File pointing to the Kero Blaster executable
     *
     * @throws NoSuchFileException If the given executable is null
     */
    public static void initGameData(final File executable) throws NoSuchFileException {
        gameData = new GameData();

        if (null == executable) {
            throw new IllegalArgumentException(Messages.getString("GameData.EXECUTABLE_NULL"));
        }
        else if (!executable.exists()) {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NONEXISTENT"),
                                                               executable.getAbsolutePath()));
        }
        gameData.executable = executable;

        final String[] dirNames = executable.getParentFile().list();
        if (null == dirNames) {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.MISSING_RSC"),
                                                               executable.getAbsolutePath()));
        }
        final ArrayList <String> directoryNames = new ArrayList <String>(Arrays.asList(dirNames));

        if (directoryNames.contains("rsc_p")) {
            gameData.resourceFolder = new File(gameData.executable.getParent() + File.separatorChar + "rsc_p");
        }
        else if (directoryNames.contains("rsc_k")) {
            gameData.resourceFolder = new File(gameData.executable.getParent() + File.separatorChar + "rsc_k");
        }
        else {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.MISSING_RSC"),
                                                               executable.getAbsolutePath()));
        }

        gameData.bgms = fillFileList("/bgm/", ".ptcop");

        gameData.maps = fillFileList("/field/", ".pxpack");

        gameData.soundEffects = fillFileList("/se/", ".ptnoise");

        gameData.scripts = fillFileList("/text/", ".pxeve");
    }

    public static File getExecutable() {
        return gameData.executable;
    }

    public static File getBaseFolder() {
        return gameData.executable.getParentFile();
    }

    public static File getResourceFolder() {
        return gameData.resourceFolder;
    }

    public static ArrayList <File> getMapList() {
        return new ArrayList <File>(gameData.maps);
    }

    public static void addMap(final File newMap) {
        gameData.maps.add(newMap);
    }

    public static void removeMap(final File map) {
        gameData.maps.remove(map);
    }

    private static ArrayList <File> fillFileList(final String pathFromResource, final String filenameExtension) {
        final File baseMapDir = new File(gameData.resourceFolder.getAbsolutePath() + File.separatorChar + pathFromResource);
        final File[] fileList = baseMapDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return pathname.getName().endsWith(filenameExtension);
            }
        });

        return (fileList != null) ? new ArrayList <File>(Arrays.asList(fileList)) : null;
    }
}