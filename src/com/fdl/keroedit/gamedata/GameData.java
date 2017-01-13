package com.fdl.keroedit.gamedata;

import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileFilter;

public class GameData {
    private File executable;
    private String resourceFolder;

    private ArrayList <File> bgms;
    private ArrayList <File> maps;
    //TODO: Images (tilesets, spritesheets, and more!)
    private ArrayList <File> soundEffects;
    private ArrayList <File> scripts;

    public GameData(final File executable, String resourceFolder) {
        this.executable = executable;

        if (!resourceFolder.startsWith("/")) {
            resourceFolder = "/" + resourceFolder;
        }
        if (!resourceFolder.endsWith("/")) {
            resourceFolder += "/";
        }
        this.resourceFolder = resourceFolder;

        bgms = fillFileList("/bgm/", ".ptcop");

        maps = fillFileList("/field/", ".pxpack");

        soundEffects = fillFileList("/se/", ".ptnoise");

        scripts = fillFileList("/text/", ".pxeve");
    }



    public File getExecutable() {
        return executable;
    }

    public String getResourceFolder() {
        return resourceFolder;
    }

    public ArrayList <File> getMapList() {
        return new ArrayList <File>(maps);
    }

    public void addMap (final File newMap) {
        insertMap(maps.size(), newMap);
    }

    public void insertMap(final int index, final File newMap) {
        maps.add(index, newMap);
    }

    public void removeMap(final File map) {
        maps.remove(map);
    }

    //TODO: private ArrayList <PxPack> fillMapList

    private ArrayList <File> fillFileList(final String pathFromResource, final String filenameExtension) {

        File baseMapDir = new File(executable.getParent() + resourceFolder + pathFromResource);
        File[] fileList = baseMapDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return pathname.getName().endsWith(filenameExtension);
            }
        });

        return (fileList != null) ? new ArrayList <File>(Arrays.asList(fileList)) : null;
    }
}