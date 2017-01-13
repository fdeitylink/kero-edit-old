//TODO: MapEditTab doesn't store the map - GameData does and MapEdit puts changes into GameData

package com.fdl.keroedit.map;

import java.util.ArrayList;

import java.io.File;
import java.util.Objects;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;

public class MapEditTab extends Tab {
    private TabPane mainTabPane;

    private PxPack map;

    public MapEditTab(File inFile) {
        super(inFile.getName().replaceAll(".pxpack", ""));

        try {
            map = new PxPack(inFile);
        }
        catch (Exception except) {
            //TODO: Legitimate exception handling code
            return;
        }

        PropertyEdit propertyEdit = new PropertyEdit(map.getHead());
        TileEdit tileEdit = new TileEdit(map.getTileLayers(), map.getEntities());

        mainTabPane = new TabPane(propertyEdit, tileEdit);

        setContent(mainTabPane);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                mainTabPane.setPrefHeight(getTabPane().getPrefHeight()); //null pointer exception - runLater probably happens too soon
                mainTabPane.setPrefWidth(getTabPane().getPrefWidth());
            }
        });

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.UNAVAILABLE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        final MapEditTab that = (MapEditTab) o;
        return Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
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
