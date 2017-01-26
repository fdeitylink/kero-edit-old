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

    private ArrayList <String> bgms;
    private ArrayList <String> maps; //TODO: Make arraylist of PxPack objects
    //TODO: Images (tilesets, spritesheets, and more!)
    private ArrayList <String> soundEffects;
    private ArrayList <String> scripts; //TODO: Make arraylist of PxEve objects

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

        gameData.bgms = fileFilenameList(File.separatorChar + "bgm" + File.separatorChar, ".ptcop");

        gameData.maps = fileFilenameList(File.separatorChar + "field" + File.separatorChar, ".pxpack");

        gameData.soundEffects = fileFilenameList(File.separatorChar + "se" + File.separatorChar, ".ptnoise");

        gameData.scripts = fileFilenameList(File.separatorChar + "text" + File.separatorChar, ".pxeve");
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

    public static ArrayList <String> getMapList() {
        return new ArrayList <String>(gameData.maps);
    }

    public static void removeMap(final String mapname) {
        gameData.maps.remove(mapname);
    }

    private static ArrayList <String> fileFilenameList(final String pathFromResource, final String filenameExtension) {
        final File baseMapDir = new File(gameData.resourceFolder.getAbsolutePath() + File.separatorChar + pathFromResource);
        final File[] fileList = baseMapDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return pathname.getName().endsWith(filenameExtension);
            }
        });

        final ArrayList <String> nameList = new ArrayList <String>(fileList.length);
        for (final File file : fileList) {
            nameList.add(file.getName().replace(filenameExtension, ""));
        }

        return nameList;
    }
}