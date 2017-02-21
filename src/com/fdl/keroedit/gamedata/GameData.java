package com.fdl.keroedit.gamedata;

import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.nio.file.NoSuchFileException;

import java.text.MessageFormat;

import com.fdl.keroedit.Messages;

//TODO: Throws errors (or mkdirs()?) if rsc_x is missing necessary folders

/**
 * Singleton class for storing about a Kero Blaster game
 */
public class GameData {
    private static GameData inst;

    private MOD_TYPE modType;

    private File executable;
    private File resourceFolder;

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
     * @param executable File pointing to the Kero Blaster executable
     *
     * @throws NoSuchFileException If the given executable is null
     */
    public static void init(final File executable) throws NoSuchFileException {
        //TODO: Move all/most of this stuff into constructor?
        inst = new GameData();

        if (null == executable) {
            throw new NullPointerException(Messages.getString("GameData.EXECUTABLE_NULL"));
        }
        else if (!executable.exists()) {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.EXECUTABLE_NONEXISTENT"),
                                                               executable.getAbsolutePath()));
        }
        inst.executable = executable;

        final String[] dirNames = executable.getParentFile().list();
        if (null == dirNames) {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.MISSING_RSC"),
                                                               executable.getAbsolutePath()));
        }
        final ArrayList <String> directoryNames = new ArrayList <>(Arrays.asList(dirNames));

        if (directoryNames.contains("rsc_p")) {
            inst.resourceFolder = new File(inst.executable.getParent() + File.separatorChar + "rsc_p");
            inst.modType = MOD_TYPE.PINK_HOUR; //TODO: Detect or ask if pink heaven
        }
        else if (directoryNames.contains("rsc_k")) {
            inst.resourceFolder = new File(inst.executable.getParent() + File.separatorChar + "rsc_k");
            inst.modType = MOD_TYPE.KERO_BLASTER;
        }
        else {
            throw new NoSuchFileException(MessageFormat.format(Messages.getString("GameData.MISSING_RSC"),
                                                               executable.getAbsolutePath()));
        }

        inst.bgms = getFileList(File.separatorChar + "bgm" + File.separatorChar, ".ptcop");

        inst.maps = getFileList(File.separatorChar + "field" + File.separatorChar, ".pxpack");

        inst.soundEffects = getFileList(File.separatorChar + "se" + File.separatorChar, ".ptnoise");

        inst.scripts = getFileList(File.separatorChar + "text" + File.separatorChar, ".pxeve");
    }

    public static File getExecutable() {
        return inst.executable;
    }

    public static File getResourceFolder() {
        return inst.resourceFolder;
    }

    public static MOD_TYPE getModType() {
        return inst.modType;
    }

    public static ArrayList <String> getMapList() {
        return new ArrayList <>(inst.maps);
    }

    public static void removeMap(final String mapname) {
        inst.maps.remove(mapname);
    }

    private static ArrayList <String> getFileList(final String pathFromResource, final String extension) {
        final File baseMapDir = new File(inst.resourceFolder.getAbsolutePath() + File.separatorChar + pathFromResource);
        final File[] fileList = baseMapDir.listFiles(pathname -> pathname.getName().endsWith(extension));

        final ArrayList <String> nameList = null != fileList ? new ArrayList <>(fileList.length) : null;
        if (null != fileList) {
            for (final File file : fileList) {
                nameList.add(file.getName().substring(0, file.getName().lastIndexOf(extension)));
            }
        }

        return nameList;
    }

    public enum MOD_TYPE {
        PINK_HOUR,
        PINK_HEAVEN,
        KERO_BLASTER
    }
}