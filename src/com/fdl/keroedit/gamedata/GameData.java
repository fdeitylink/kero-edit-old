package com.fdl.keroedit.gamedata;

import java.util.ArrayList;

import java.io.File;
import java.io.FileFilter;

public class GameData {
    private File resourceFolder;
    private ArrayList <File> bgms;
    private ArrayList <File> maps;
    //TODO: Images (tilesets, spritesheets, and more!)
    private ArrayList <File> soundEffects;
    private ArrayList <File> scripts;

    public GameData(File resourceFolder) {
        this.resourceFolder = resourceFolder;

        bgms = new ArrayList <File>();
        fillFileList(bgms, "/bgm/", ".ptcop");

        maps = new ArrayList <File>();
        fillFileList(maps, "/field/", ".pxpack");

        soundEffects = new ArrayList <File>();
        fillFileList(soundEffects, "/se/", ".ptnoise");

        scripts = new ArrayList <File>();
        fillFileList(scripts, "/text/", ".pxeve");
    }

    public void save() {

    }

    public File getResourceFolder() {
        return new File(resourceFolder.getAbsolutePath());
    }

    public ArrayList <File> getMapList() {
        return new ArrayList <File>(maps);
    }

    private void fillFileList(ArrayList list, String pathFromResource, String filenameExtension) {
        list.clear();

        File baseMapDir = new File(resourceFolder.getAbsolutePath() + pathFromResource);
        File[] fileList = baseMapDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.getName().endsWith(filenameExtension)) {
                    return true;
                }
                return false;
            }
        });

        if (null != fileList) {
            for (File f : fileList) {
                list.add(f);
            }
            return;
        }
    }
}