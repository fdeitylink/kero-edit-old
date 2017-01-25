//TODO: MapEditTab doesn't store the map - GameData does and MapEdit puts changes into GameData

package com.fdl.keroedit.mapedit;

import java.io.File;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import javafx.application.Platform;

import javafx.scene.layout.Pane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import javafx.scene.control.Button;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.RadioButton;
import javafx.scene.control.CheckBox;

import javafx.scene.control.Alert;

import java.util.Optional;

import javafx.util.Pair;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.beans.value.ObservableValue;
import javafx.beans.value.ChangeListener;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;
import javafx.geometry.Rectangle2D;

import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;

import javafx.scene.paint.Color;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.image.PixelFormat;

import java.nio.ByteBuffer;

import com.fdl.keroedit.util.JavaFXUtil;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.Config;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.map.PxPack;

public class MapEditTab extends Tab {
    private /*final*/ TabPane mainTabPane;

    private PropertyEditTab propertyEditTab;
    private TileEditTab tileEditTab;
    private ScriptEditTab scriptEditTab;

    private /*final*/ PxPack map;

    public MapEditTab(final File inFile) {
        try {
            map = new PxPack(inFile);
        }
        catch (final IOException | ParseException except) {
            //TODO: Legitimate exception handling code
            return;
        }

        setText(map.getName());
        setId(map.getName());
        setTooltip(new Tooltip(inFile.getAbsolutePath()));

        //TODO: Context menu? close and rename

        propertyEditTab = new PropertyEditTab();
        tileEditTab = new TileEditTab();
        scriptEditTab = new ScriptEditTab(map.getHead().getScriptName());

        mainTabPane = new TabPane(propertyEditTab, tileEditTab, scriptEditTab);

        setContent(mainTabPane);

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.UNAVAILABLE);

        //TODO: Do this directly here? or will tab be created in task on separate thread?
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (null != getTabPane()) {
                    mainTabPane.setPrefHeight(getTabPane().getPrefHeight());
                    mainTabPane.setPrefWidth(getTabPane().getPrefWidth());
                }
            }
        });
    }

    public void setMapZoom(final int zoom) {
        tileEditTab.mapPane.zoom.set(zoom);
    }

    public void setTilesetZoom(final int zoom) {
        tileEditTab.tilesetPane.zoom.set(zoom);
    }

    public void setTilesetBgColor(final Color col) {
        tileEditTab.tilesetPane.bgColor.set(col);
    }

    private class PropertyEditTab extends Tab {
        private final GridPane mainGridPane;

        private final PxPack.Head head;

        PropertyEditTab() {
            super(Messages.getString("MapEditTab.PropertyEditTab.TITLE"));

            head = map.getHead();

            mainGridPane = new GridPane();
        }
    }

    private class TileEditTab extends Tab {
        private static final int TILE_WIDTH = 8;
        private static final int TILE_HEIGHT = 8;

        private static final int TILESET_WIDTH = 128;
        private static final int TILESET_HEIGHT = 128;

        private final PxPack.Head head; //Stores it as some properties are necessary but does not modify it
        //private final ArrayList<PxPack.Entity> entities;

        private final BorderPane mainBorderPane;

        private final TilesetPane tilesetPane;
        private final MapPane mapPane;

        private int selectedLayer;
        private final SimpleBooleanProperty[] displayedlayers;

        TileEditTab() {
            super(Messages.getString("MapEditTab.TileEditTab.TITLE"));

            head = map.getHead(); //TODO: Store same head as properties tab in order to automatically update properties?
            //entities = map.getEntities();

            final PxPack.TileLayer[] tileLayers = map.getTileLayers();

            mainBorderPane = new BorderPane();

            int i;
            for (i = 0; i < tileLayers.length; ++i) {
                //selects first 'valid' layer
                if (null != tileLayers[i].getTiles() && null != head.getTilesetNames()[i]) {
                    break;
                }
            }
            selectedLayer = i;

            displayedlayers = new SimpleBooleanProperty[tileLayers.length];
            for (i = 0; i < displayedlayers.length; ++i) {
                displayedlayers[i] = new SimpleBooleanProperty(true);
            }

            tilesetPane = new TilesetPane();
            mainBorderPane.setRight(tilesetPane);

            mapPane = new MapPane();
            mainBorderPane.setCenter(mapPane);

            mainBorderPane.setTop(new SettingsPane());

            setContent(mainBorderPane);
        }

        private class TilesetPane extends VBox {
            private final SimpleIntegerProperty zoom;
            private final SimpleObjectProperty <Color> bgColor;

            private final Image[] tilesets;
            private final ImageView tilesetImgView;
            private final ScrollPane tilesetScrollPane;
            private final int[] selectedTiles;
            private final ImageView selectedTileImgView;

            TilesetPane() {
                final String[] tilesetNames = head.getTilesetNames();
                tilesets = new Image[tilesetNames.length];
                for (int i = 0; i < tilesets.length; ++i) {
                    tilesets[i] = new Image("file:///" + GameData.getResourceFolder().getAbsolutePath() + "/img/" +
                                            tilesetNames[i] + ".png");
                    while (1 != tilesets[i].getProgress()) {
                    }
                }

                zoom = new SimpleIntegerProperty(Config.tilesetZoom);
                zoom.addListener(new ChangeListener <Number>() {
                    @Override
                    public void changed(final ObservableValue <? extends Number> observable, final Number oldValue, final Number newValue) {
                        fixDisplay();
                    }
                });

                //TODO: draw square around selected tile in tileset
                tilesetImgView = new ImageView();
                fixDisplay();

                selectedTiles = new int[tilesets.length];

                final Pane tilesetPane = new Pane(tilesetImgView); //used to allow a background color behind the tileset
                                                                    //as well as selecting blank or partially-blank tiles
                                                                    //since ImageView won't detect clicks on blank regions
                tilesetPane.setOnMouseClicked(new EventHandler <MouseEvent>() {
                    @Override
                    public void handle(final MouseEvent event) {
                        if (event.getButton().equals(MouseButton.PRIMARY)) {
                            if (null == tilesetImgView.getImage()) {
                                selectedTiles[selectedLayer] = 0;
                            }
                            final int x = (int)(event.getX() / zoom.get() / TILE_WIDTH);
                            final int y = (int)(event.getY() / zoom.get() / TILE_HEIGHT);
                            final int width = TILESET_WIDTH /* * tilesetZoom.get() / tilesetZoom.get()*/ / TILE_WIDTH;

                            selectedTiles[selectedLayer] = (y * width) + x;

                            displaySelectedTile();
                        }
                    }
                });

                bgColor = new SimpleObjectProperty <Color>(Config.tilesetBgColor);
                bgColor.addListener(new ChangeListener <Color>() {
                    @Override
                    public void changed(final ObservableValue <? extends Color> observable, final Color oldValue,
                                        final Color newValue) {
                        tilesetPane.setBackground(new Background(new BackgroundFill(newValue, CornerRadii.EMPTY,
                                                                                    Insets.EMPTY)));
                    }
                });
                tilesetPane.setBackground(new Background(new BackgroundFill(bgColor.get(), CornerRadii.EMPTY,
                                                                            Insets.EMPTY)));

                tilesetScrollPane = new ScrollPane(tilesetPane);
                tilesetScrollPane.setPrefWidth(TILESET_WIDTH * 2);
                tilesetScrollPane.setMaxWidth(TILESET_WIDTH * 2);
                tilesetScrollPane.setPrefHeight(TILESET_HEIGHT * 2);
                tilesetScrollPane.setMaxHeight(TILESET_HEIGHT * 2);

                selectedTileImgView = new ImageView();
                displaySelectedTile();

                getChildren().addAll(tilesetScrollPane, selectedTileImgView);
            }

            private void fixDisplay() {
                tilesetImgView.setImage(JavaFXUtil.scaleImage(tilesets[selectedLayer], zoom.get()));
                tilesetImgView.setViewport(new Rectangle2D(0, 0, TILESET_WIDTH * zoom.get(),
                                                           TILESET_HEIGHT * zoom.get()));
            }

            private void displaySelectedTile() {
                final WritableImage tileImg = new WritableImage(TILE_WIDTH, TILE_HEIGHT);
                final PixelWriter tileWriter = tileImg.getPixelWriter();

                final PixelReader tilesetReader = tilesets[selectedLayer].getPixelReader();

                final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                final int tilesetX = selectedTiles[selectedLayer] % (TILESET_WIDTH / TILE_WIDTH);
                final int tilesetY = selectedTiles[selectedLayer] / (TILESET_HEIGHT / TILE_HEIGHT);

                final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];
                tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT, pxFormat,
                                        tile, 0, TILE_WIDTH * 4);

                tileWriter.setPixels(0, 0, TILE_WIDTH, TILE_HEIGHT, pxFormat, tile, 0, TILE_WIDTH * 4);

                selectedTileImgView.setImage(JavaFXUtil.scaleImage(tileImg, (int)(TILESET_WIDTH * 2 / tileImg.getWidth())));
            }
        }

        private class MapPane extends ScrollPane {
            private final SimpleIntegerProperty zoom;

            private final StackPane displayPane;

            private final PxPack.TileLayer[] tileLayers;
            private final Canvas[] layerCanvases;

            MapPane() {
                zoom = new SimpleIntegerProperty(Config.mapZoom);
                zoom.addListener(new ChangeListener <Number>() {
                    @Override
                    public void changed(final ObservableValue <? extends Number> observable, final Number oldValue,
                                        final Number newValue) {

                        for (int i = 0; i < layerCanvases.length; ++i) {
                            fixLayerCanvasSize(i);
                            drawLayer(i);
                        }
                        fixDisplaySize();
                    }
                });

                tileLayers = map.getTileLayers();

                layerCanvases = new Canvas[tileLayers.length];
                for (int i = 0; i < layerCanvases.length; ++i) {
                    layerCanvases[i] = new Canvas();
                    fixLayerCanvasSize(i);
                    drawLayer(i);
                }

                displayPane = new StackPane();
                displayPane.setAlignment(Pos.TOP_LEFT);

                //TODO: Make change listener for color
                displayPane.setBackground(new Background(new BackgroundFill(head.getBgColor(), CornerRadii.EMPTY,
                                                                            Insets.EMPTY)));

                for (int i = layerCanvases.length - 1; i > -1; --i) {
                    displayPane.getChildren().add(layerCanvases[i]);
                }
                fixDisplaySize();

                displayPane.setOnMouseClicked(new EventHandler <MouseEvent>() {
                    @Override
                    public void handle(final MouseEvent event) {
                        if (event.getButton().equals(MouseButton.PRIMARY) && null != tileLayers[selectedLayer].getTiles()) {
                            final int x = (int)(event.getX() / zoom.get() / TILE_WIDTH);
                            final int y = (int)(event.getY() / zoom.get() / TILE_HEIGHT);

                            if (y < tileLayers[selectedLayer].getTiles().length &&
                                x < tileLayers[selectedLayer].getTiles()[0].length) {

                                tileLayers[selectedLayer].setTile(x, y, tilesetPane.selectedTiles[selectedLayer]);
                                drawLayer(selectedLayer);
                            }
                        }
                    }
                });

                setContent(displayPane);
            }

            /**
             * Draws the given layer to its respective canvas
             *
             * @param layerIndex The index of the layer to draw
             */
            private void drawLayer(final int layerIndex) {
                layerCanvases[layerIndex].getGraphicsContext2D().clearRect(0, 0, layerCanvases[layerIndex].getWidth(),
                                                                           layerCanvases[layerIndex].getHeight());
                if (!displayedlayers[layerIndex].get()) {
                    return;
                }

                final int[][] layerData = tileLayers[layerIndex].getTiles();
                //TODO: remove tileset name check?
                if (null == layerData || null == head.getTilesetNames()[layerIndex]) {
                    return;
                    //TODO: do something else?
                }

                final PixelReader tilesetReader = tilesetPane.tilesets[layerIndex].getPixelReader();
                if (null == tilesetReader) { //this shouldn't happen since it waits for them to be loaded
                    return;
                    //TODO: do something else?
                }

                final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                final WritableImage tmpLayerImg = new WritableImage(layerData[0].length * TILE_WIDTH,
                                                                    layerData.length * TILE_HEIGHT);
                final PixelWriter tmpLayerImgWriter = tmpLayerImg.getPixelWriter();

                for (int y = 0; y < layerData.length; ++y) {
                    for (int x = 0; x < layerData[y].length; ++x) {
                        final int tilesetX = layerData[y][x] % (TILESET_WIDTH / TILE_WIDTH);
                        final int tilesetY = layerData[y][x] / (TILESET_HEIGHT / TILE_HEIGHT);

                        final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];
                        tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT,
                                                pxFormat, tile, 0, TILE_WIDTH * 4);

                        tmpLayerImgWriter.setPixels(x * TILE_WIDTH, y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT, pxFormat,
                                                    tile, 0, TILE_WIDTH * 4);
                    }
                }

                layerCanvases[layerIndex].getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tmpLayerImg, zoom.get()),
                                                                           0, 0);
            }

            private void fixLayerCanvasSize(final int index) {
                if (null != tileLayers[index].getTiles()) {
                    //Note to self: Do not use resize() instead of individual width/height calls - it doesn't work for some reason
                    layerCanvases[index].setWidth(tileLayers[index].getTiles()[0].length * TILE_WIDTH * zoom.get());
                    layerCanvases[index].setHeight(tileLayers[index].getTiles().length * TILE_HEIGHT * zoom.get());
                }
                else {
                    layerCanvases[index].setWidth(0);
                    layerCanvases[index].setHeight(0);
                }
            }

            private void fixDisplaySize() {
                int maxWidth = (int)layerCanvases[0].getWidth();
                int maxHeight = (int)layerCanvases[0].getHeight();

                for (int i = 1; i < layerCanvases.length; ++i) {
                    if (layerCanvases[i].getWidth() > maxWidth) {
                        maxWidth = (int)layerCanvases[i].getWidth();
                    }

                    if (layerCanvases[i].getHeight() > maxHeight) {
                        maxHeight = (int)layerCanvases[i].getHeight();
                    }
                }

                //Note to self - apply these to the map display pane (like it is now), not the scroll pane this extends,
                //as otherwise the pane is placed as close to center as possible by the border pane,
                //which looks weird on certain zoom levels
                displayPane.setPrefWidth(maxWidth);
                displayPane.setMaxWidth(maxWidth);

                displayPane.setPrefHeight(maxHeight);
                displayPane.setMaxHeight(maxHeight);
            }
        }

        private class SettingsPane extends GridPane {
            SettingsPane() {
                setPadding(new Insets(10, 10, 10, 10));
                setVgap(10);
                setHgap(20);

                final Text displayedLayersLabel = new Text(Messages.getString("MapEditTab.TileEditTab.DISPLAYED_LAYERS"));
                displayedLayersLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
                add(displayedLayersLabel, 0, 0);

                final CheckBox[] displayedLayersCheckboxes = new CheckBox[mapPane.tileLayers.length];
                for (int i = 0; i < displayedLayersCheckboxes.length; ++i) {
                    displayedLayersCheckboxes[i] = new CheckBox(Messages.getString(0 == i ?
                                                                                   "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                          : 1 == i ?
                                                                                            "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                   : "MapEditTab.TileEditTab.Layers.BACKGROUND"));
                    displayedLayersCheckboxes[i].setAllowIndeterminate(false);
                    displayedLayersCheckboxes[i].setSelected(true);
                    displayedlayers[i].bind(displayedLayersCheckboxes[i].selectedProperty());

                    final int layer = i;
                    displayedLayersCheckboxes[i].selectedProperty().addListener(new ChangeListener <Boolean>() {
                        @Override
                        public void changed(final ObservableValue <? extends Boolean> observable, final Boolean oldValue,
                                            final Boolean newValue) {

                            //layerIsDisplayed[layer] = newValue;
                            mapPane.drawLayer(layer);
                        }
                    });
                    add(displayedLayersCheckboxes[i], 0, i + 1);
                }

                final Text selectedLayerLabel = new Text(Messages.getString("MapEditTab.TileEditTab.SELECTED_LAYER"));
                selectedLayerLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
                add(selectedLayerLabel, 1, 0);

                final ToggleGroup selectedLayerToggleGroup = new ToggleGroup();
                final RadioButton[] selectedLayerRadioButtons = new RadioButton[mapPane.tileLayers.length];
                for (int i = 0; i < selectedLayerRadioButtons.length; ++i) {
                    selectedLayerRadioButtons[i] = new RadioButton(Messages.getString(0 == i ?
                                                                                      "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                             : 1 == i ?
                                                                                               "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                      : "MapEditTab.TileEditTab.Layers.BACKGROUND"));

                    selectedLayerRadioButtons[i].setToggleGroup(selectedLayerToggleGroup);

                    if (selectedLayer == i) {
                        selectedLayerRadioButtons[i].setSelected(true);
                    }

                    final int layer = i;
                    selectedLayerRadioButtons[i].selectedProperty().addListener(new ChangeListener <Boolean>() {
                        @Override
                        public void changed(final ObservableValue <? extends Boolean> observable, final Boolean oldValue,
                                            final Boolean newValue) {

                            if (newValue) {
                                selectedLayer = layer;
                                tilesetPane.fixDisplay();
                                tilesetPane.displaySelectedTile();
                            }
                        }
                    });
                    add(selectedLayerRadioButtons[i], 1, i + 1);
                }

                final Button resizeButton = new Button(Messages.getString("MapEditTab.TileEditTab.Resize.BUTTON_TEXT"));
                resizeButton.setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final String title = "Resize " + Messages.getString(0 == selectedLayer ?
                                                                            "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                               : 1 == selectedLayer ?
                                                                                                 "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                                    : "MapEditTab.TileEditTab.Layers.BACKGROUND");
                        final int width = null == mapPane.tileLayers[selectedLayer].getTiles() ? 0 :
                                          mapPane.tileLayers[selectedLayer].getTiles()[0].length;
                        final int height = null == mapPane.tileLayers[selectedLayer].getTiles() ? 0 :
                                           mapPane.tileLayers[selectedLayer].getTiles().length;

                        final String currentSizeStr = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.CURRENT_SIZE"),
                                                                           width, height);

                        final Optional <Pair <String, String>> result = JavaFXUtil.createDualTextFieldDialog(title, currentSizeStr,
                                                                                                             Messages.getString("MapEditTab.TileEditTab.Resize.NEW_WIDTH"),
                                                                                                             Messages.getString("MapEditTab.TileEditTab.Resize.NEW_HEIGHT"),
                                                                                                             Messages.getString("MapEditTab.TileEditTab.Resize.DIALOG_OK"))
                                                                                  .showAndWait();

                        if (result.isPresent()) {
                            final String widthStr = result.get().getKey().replaceAll("[^0-9]", "");
                            final String heightStr = result.get().getValue().replaceAll("[^0-9]", "");

                            if (0 < widthStr.length() && 0 < heightStr.length()) {
                                int newWidth;
                                int newHeight;
                                try {
                                    newWidth = Integer.parseInt(widthStr);
                                    newHeight = Integer.parseInt(heightStr);
                                }
                                catch (final NumberFormatException except) {
                                    JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                           Messages.getString("MapEditTab.TileEditTab.Resize.NumFormatExcept.TITLE"),
                                                           null,
                                                           Messages.getString("MapEditTab.TileEditTab.Resize.NumFormatExcept.MESSAGE"))
                                              .showAndWait();
                                    return;
                                }

                                if (newWidth > 0xFFFF || newHeight > 0xFFFF) {
                                    JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                           Messages.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.TITLE"),
                                                           null,
                                                           Messages.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.MESSAGE")).showAndWait();
                                    return;
                                }

                                mapPane.tileLayers[selectedLayer].resize(newWidth, newHeight);

                                mapPane.fixLayerCanvasSize(selectedLayer);
                                mapPane.fixDisplaySize();
                                mapPane.drawLayer(selectedLayer);
                            }
                        }

                    }
                });
                add(resizeButton, 2, 0);
            }
        }
    }
}