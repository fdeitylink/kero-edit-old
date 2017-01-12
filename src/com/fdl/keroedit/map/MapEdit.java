package com.fdl.keroedit.map;

import java.util.ArrayList;

import java.io.File;

import javafx.application.Platform;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;

public class MapEdit extends Tab {
    private TabPane mainTabPane;

    private PxPack map;

    public MapEdit(File inFile) {
        super();
        try {
            map = new PxPack(inFile);
        }
        catch (Exception except) {

            return;
        }
        setText(map.getFile().getName().replaceAll(".pxpack", ""));

        PropertyEdit propertyEdit = new PropertyEdit(map.getHead());
        TileEdit tileEdit = new TileEdit(map.getTileLayers(), map.getEntities());

        mainTabPane = new TabPane(propertyEdit, tileEdit);

        setContent(mainTabPane);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                mainTabPane.setPrefHeight(getTabPane().getPrefHeight());
                mainTabPane.setPrefWidth(getTabPane().getPrefWidth());
            }
        });

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.UNAVAILABLE);
    }

    private class PropertyEdit extends Tab {
        private PxPack.Head head;

        PropertyEdit(PxPack.Head head) {
            super("Properties");
            this.head = head;
        }
    }

    private class TileEdit extends Tab {
        private PxPack.TileLayer[] tileLayers;
        private ArrayList <PxPack.Entity> entities;

        TileEdit(PxPack.TileLayer[] tileLayers, ArrayList <PxPack.Entity> entities) {
            super("Map");
            this.tileLayers = tileLayers;
            this.entities = entities;
        }
    }

    /*private class ScriptEdit extends Tab {
    }*/
}
