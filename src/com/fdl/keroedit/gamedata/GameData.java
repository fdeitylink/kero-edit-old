package com.fdl.keroedit.gamedata;

import java.util.ArrayList;

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

    public GameData(final File executable, final String resourceFolder) {
        this.executable = executable;

        this.resourceFolder = "/" + resourceFolder + "/";

        bgms = new ArrayList <File>();
        fillFileList(bgms, "/bgm/", ".ptcop");

        maps = new ArrayList <File>();
        fillFileList(maps, "/field/", ".pxpack");

        soundEffects = new ArrayList <File>();
        fillFileList(soundEffects, "/se/", ".ptnoise");

        scripts = new ArrayList <File>();
        fillFileList(scripts, "/text/", ".pxeve");
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

    private void fillFileList(ArrayList list, String pathFromResource, String filenameExtension) {
        list.clear();

        File baseMapDir = new File(executable.getParent() + resourceFolder + pathFromResource);
        File[] fileList = baseMapDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(filenameExtension);
            }
        });

        if (null != fileList) {
            for (File f : fileList) {
                list.add(f);
            }
        }
    }
}