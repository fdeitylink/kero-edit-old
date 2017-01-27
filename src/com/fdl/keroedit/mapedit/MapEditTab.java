//TODO: Decide if loading should cater to maps like Area* and tilsets like mptArea (could be leftover/unintended)
// - game glitches if one of those maps is loaded, so probably shouldn't cater to it

package com.fdl.keroedit.mapedit;

import java.io.File;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

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

import javafx.scene.paint.Color;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.image.PixelFormat;

import java.nio.ByteBuffer;

import javafx.concurrent.Task;

import com.fdl.keroedit.util.JavaFXUtil;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.Config;

import com.fdl.keroedit.resource.ResourceManager;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.KeroEdit;

import com.fdl.keroedit.map.PxPack;
import com.fdl.keroedit.map.PxAttr;

public class MapEditTab extends Tab {
    private /*final*/ TabPane mainTabPane;

    private PropertyEditTab propertyEditTab;
    private TileEditTab tileEditTab;
    private ScriptEditTab scriptEditTab;

    private /*final*/ PxPack map;

    public MapEditTab(final String inFileName) {
        final String fullPath = GameData.getResourceFolder().getAbsolutePath() +
                                File.separatorChar + "field" + File.separatorChar + inFileName + ".pxpack";
        try {
            map = new PxPack(new File(fullPath));
        }
        catch (final IOException | ParseException except) {
            JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("MapEditTab.ParseExcept.TITLE"), null,
                                   MessageFormat.format(Messages.getString("MapEditTab.ParseExcept.MESSAGE"), inFileName,
                                                        except.getMessage())).showAndWait();
            getTabPane().getTabs().remove(this);
        }

        setText(inFileName);
        setId(inFileName);
        setTooltip(new Tooltip(fullPath));

        //TODO: Context menu? close and rename

        propertyEditTab = new PropertyEditTab();
        tileEditTab = new TileEditTab();
        scriptEditTab = new ScriptEditTab(map.getHead().getScriptName());

        mainTabPane = new TabPane(propertyEditTab, tileEditTab, scriptEditTab);

        setContent(mainTabPane);

        mainTabPane.tabClosingPolicyProperty().setValue(TabPane.TabClosingPolicy.UNAVAILABLE);
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

        private static final int TILESET_WIDTH = TILE_WIDTH * 16;
        private static final int TILESET_HEIGHT = TILE_HEIGHT * 16;

        private static final int PXATTR_TILE_WIDTH = 16;
        private static final int PXATTR_TILE_HEIGHT = 16;

        private static final int PXATTR_IMAGE_WIDTH = PXATTR_TILE_WIDTH * 16;
        private static final int PXATTR_IMAGE_HEIGHT = PXATTR_TILE_HEIGHT * 16;

        private final PxPack.Head head; //Stores it as some properties are necessary but does not modify it
        //private final ArrayList<PxPack.Entity> entities;

        private final Image pxAttrImg;
        private PxAttr[] pxAttrs;

        private final BorderPane mainBorderPane;

        private final MapPane mapPane;
        private final TilesetPane tilesetPane;

        private final SimpleIntegerProperty selectedLayer;
        private final SimpleBooleanProperty[] displayedLayers;

        private final SimpleBooleanProperty displayTileTypes;

        //create property for mode - tile or entity

        TileEditTab() {
            super(Messages.getString("MapEditTab.TileEditTab.TITLE"));

            head = map.getHead(); //TODO: Store same head as properties tab in order to automatically update properties?
            //entities = map.getEntities();

            mainBorderPane = new BorderPane();

            displayTileTypes = new SimpleBooleanProperty(false);

            loadPxAttrs();
            pxAttrImg = ResourceManager.getImage("assist/attribute.png");

            final PxPack.TileLayer[] tileLayers = map.getTileLayers();
            int i;
            for (i = 0; i < tileLayers.length; ++i) {
                //selects first 'valid' layer
                if (null != tileLayers[i].getTiles() && null != head.getTilesetNames()[i]) {
                    break;
                }
            }
            selectedLayer = new SimpleIntegerProperty(i);

            tilesetPane = new TilesetPane();
            mainBorderPane.setRight(tilesetPane);

            displayedLayers = new SimpleBooleanProperty[tileLayers.length];
            for (i = 0; i < displayedLayers.length; ++i) {
                displayedLayers[i] = new SimpleBooleanProperty(true);
            }

            mapPane = new MapPane();
            mainBorderPane.setCenter(mapPane);

            mainBorderPane.setTop(new SettingsPane());

            setContent(mainBorderPane);
        }

        private void loadPxAttrs() {
            final String[] tilesetNames = head.getTilesetNames();
            pxAttrs = new PxAttr[tilesetNames.length];

            for (int i = 0; i < tilesetNames.length; ++i) {
                final File pxAttrFile = new File(GameData.getResourceFolder().getAbsolutePath() +
                                                 File.separatorChar + "img" + File.separatorChar +
                                                 tilesetNames[i] + ".pxattr");
                try {
                    pxAttrs[i] = new PxAttr(pxAttrFile);
                }
                catch (final IOException | ParseException except) {
                    //TODO: legitimate exception handling code
                    System.out.println(except.getMessage());
                }
            }
        }

        private class TilesetPane extends VBox {
            private static final int PANE_WIDTH = TILESET_WIDTH * 2;
            private static final int PANE_HEIGHT = TILESET_HEIGHT * 2;

            private final SimpleIntegerProperty zoom;
            private final SimpleObjectProperty <Color> bgColor;

            private Image[] tilesets;
            private final Canvas tilesetCanvas;
            private final int[] selectedTiles;
            private final ImageView selectedTileImgView;

            TilesetPane() {
                zoom = new SimpleIntegerProperty(Config.tilesetZoom);
                zoom.addListener(new ChangeListener <Number>() {
                    @Override
                    public void changed(final ObservableValue <? extends Number> observable, final Number oldValue,
                                        final Number newValue) {
                        redrawTileset();
                        if (displayTileTypes.get()) {
                            redrawTileTypes();
                        }
                    }
                });

                loadTilesets();

                selectedLayer.addListener(new ChangeListener <Number>() {
                    @Override
                    public void changed(final ObservableValue <? extends Number> observable, final Number oldValue,
                                        final Number newValue) {
                        redrawTileset();
                        if (displayTileTypes.get()) {
                            redrawTileTypes();
                        }
                        displaySelectedTile();
                    }
                });

                displayTileTypes.addListener(new ChangeListener <Boolean>() {
                    @Override
                    public void changed(final ObservableValue <? extends Boolean> observable, final Boolean oldValue,
                                        final Boolean newValue) {
                        if (newValue) {
                            redrawTileTypes();
                        }
                        else {
                            redrawTileset();
                        }
                    }
                });

                tilesetCanvas = new Canvas();
                //TODO: draw square around selected tile in tileset
                selectedTiles = new int[tilesets.length];
                tilesetCanvas.setOnMouseClicked(new EventHandler <MouseEvent>() {
                    @Override
                    public void handle(final MouseEvent event) {
                        if (event.getButton().equals(MouseButton.PRIMARY)) {
                            if (0 == tilesets[selectedLayer.get()].getWidth()) { //doesn't have any dimension/exist
                                selectedTiles[selectedLayer.get()] = 0;
                                return;
                            }
                            final int x = (int)(event.getX() / zoom.get() / TILE_WIDTH);
                            final int y = (int)(event.getY() / zoom.get() / TILE_HEIGHT);
                            final int width = TILESET_WIDTH /* * tilesetZoom.get() / tilesetZoom.get()*/ / TILE_WIDTH;

                            selectedTiles[selectedLayer.get()] = (y * width) + x;

                            displaySelectedTile();
                        }
                    }
                });

                bgColor = new SimpleObjectProperty <Color>(Config.tilesetBgColor);
                bgColor.addListener(new ChangeListener <Color>() {
                    @Override
                    public void changed(final ObservableValue <? extends Color> observable, final Color oldValue,
                                        final Color newValue) {

                        redrawTileset();
                    }
                });
                redrawTileset();

                final ScrollPane tilesetScrollPane = new ScrollPane(tilesetCanvas);
                tilesetScrollPane.setMinWidth(PANE_WIDTH);
                tilesetScrollPane.setMaxWidth(PANE_WIDTH);
                tilesetScrollPane.setMinHeight(PANE_HEIGHT);
                tilesetScrollPane.setMaxHeight(PANE_HEIGHT);

                selectedTileImgView = new ImageView();
                displaySelectedTile();

                getChildren().addAll(tilesetScrollPane, selectedTileImgView);
            }

            //TODO: Split apart into multiple methods?
            private void redrawTileset() {
                tilesetCanvas.setWidth(TILESET_WIDTH * zoom.get());
                tilesetCanvas.setHeight(TILESET_HEIGHT * zoom.get());

                tilesetCanvas.getGraphicsContext2D().setFill(bgColor.get());
                tilesetCanvas.getGraphicsContext2D().fillRect(0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());

                tilesetCanvas.getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tilesets[selectedLayer.get()],
                                                                                     zoom.get()),
                                                               0, 0);

                //Since right now I only load the necessary part of the tileset, a simpler drawImage() method can be called (^)
                /*tilesetCanvas.getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tilesets[selectedLayer.get()], zoom.get()),
                                                               0, 0, TILESET_WIDTH * zoom.get(), TILESET_HEIGHT * zoom.get(),
                                                               0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());*/
            }

            private void redrawTileTypes() {
                final int[][] attributes = pxAttrs[selectedLayer.get()].getAttributes();
                if (null != attributes) {
                    for (int y = 0; y < attributes[0].length; ++y) {
                        for (int x = 0; x < attributes.length; ++x) {
                            final int attributesX = attributes[y][x] % (PXATTR_IMAGE_WIDTH / PXATTR_TILE_WIDTH);
                            final int attributesY = attributes[y][x] / (PXATTR_IMAGE_HEIGHT / PXATTR_TILE_HEIGHT);

                            final WritableImage tileTypeImg = new WritableImage(pxAttrImg.getPixelReader(),
                                                                                attributesX * PXATTR_TILE_WIDTH,
                                                                                attributesY * PXATTR_TILE_HEIGHT,
                                                                                PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT);

                            final int scale = zoom.get() / 2;

                            tilesetCanvas.getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tileTypeImg, scale),
                                                                           x * PXATTR_TILE_WIDTH * scale,
                                                                           y * PXATTR_TILE_HEIGHT * scale);
                        }
                    }
                }
            }

            private void displaySelectedTile() {
                final Image tilesetImg = tilesets[selectedLayer.get()];

                final int tilesetX = selectedTiles[selectedLayer.get()] % (TILESET_WIDTH / TILE_WIDTH);
                final int tilesetY = selectedTiles[selectedLayer.get()] / (TILESET_HEIGHT / TILE_HEIGHT);

                final WritableImage tileImg = (0 != tilesetImg.getWidth()) ?
                                              new WritableImage(tilesetImg.getPixelReader(), tilesetX * TILE_WIDTH,
                                                                tilesetY * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT) :
                                              new WritableImage(TILE_WIDTH, TILE_HEIGHT);

                selectedTileImgView.setImage(JavaFXUtil.scaleImage(tileImg, (int)(PANE_WIDTH / tileImg.getWidth())));
            }

            private void loadTilesets() {
                final String[] tilesetNames = head.getTilesetNames();
                tilesets = new Image[tilesetNames.length];

                for (int i = 0; i < tilesets.length; ++i) {
                    final Image uncroppedTileset = new Image("file:///" + GameData.getResourceFolder().getAbsolutePath() +
                                                             File.separatorChar + "img" + File.separatorChar + tilesetNames[i] + ".png");
                    while (1 != uncroppedTileset.getProgress()) {
                    }

                    //TODO: Figure out purpose of the rest of the tileset and see if it's worth storing (doesn't look to be right now)
                    final Image croppedTileset = (0 != uncroppedTileset.getWidth()) ?
                                                 new WritableImage(uncroppedTileset.getPixelReader(), 0, 0,
                                                                   TILESET_WIDTH, TILESET_HEIGHT) : uncroppedTileset;

                    tilesets[i] = croppedTileset;
                }
            }
        }

        private class MapPane extends ScrollPane {
            private final SimpleIntegerProperty zoom;

            //TODO: Add listener for size for when a layer is resized?
            private final PxPack.TileLayer[] tileLayers;
            private final Canvas mapCanvas;

            MapPane() {
                zoom = new SimpleIntegerProperty(Config.mapZoom);
                zoom.addListener(new ChangeListener <Number>() {
                    @Override
                    public void changed(final ObservableValue <? extends Number> observable, final Number oldValue,
                                        final Number newValue) {

                        fixSize();
                        redrawMap();
                    }
                });

                for (final SimpleBooleanProperty bool : displayedLayers) {
                    bool.addListener(new ChangeListener <Boolean>() {
                        @Override
                        public void changed(final ObservableValue <? extends Boolean> observable, final Boolean oldValue,
                                            final Boolean newValue) {
                            redrawMap();
                        }
                    });
                }

                displayTileTypes.addListener(new ChangeListener <Boolean>() {
                    @Override
                    public void changed(final ObservableValue <? extends Boolean> observable, final Boolean oldValue, final Boolean newValue) {
                        redrawMap();
                    }
                });

                tileLayers = map.getTileLayers();

                mapCanvas = new Canvas();
                mapCanvas.setOnMouseClicked(new EventHandler <MouseEvent>() {
                    @Override
                    public void handle(final MouseEvent event) {
                        if (event.getButton().equals(MouseButton.PRIMARY) &&
                            null != tileLayers[selectedLayer.get()].getTiles()) {

                            final int x = (int)(event.getX() / zoom.get() / TILE_WIDTH);
                            final int y = (int)(event.getY() / zoom.get() / TILE_HEIGHT);

                            if (y < tileLayers[selectedLayer.get()].getTiles().length &&
                                x < tileLayers[selectedLayer.get()].getTiles()[0].length) {

                                tileLayers[selectedLayer.get()].setTile(x, y, tilesetPane.selectedTiles[selectedLayer.get()]);
                                redrawMap();
                            }
                        }
                    }
                });
                fixSize();
                redrawMap();

                setPannable(false); //conflicts with drag

                setVbarPolicy(ScrollBarPolicy.ALWAYS);
                setHbarPolicy(ScrollBarPolicy.ALWAYS);

                setContent(mapCanvas);
            }

            /**
             * Draws the map to the stored canvas
             */
            private void redrawMap() {
                //TODO: Change listener for background color
                mapCanvas.getGraphicsContext2D().setFill(head.getBgColor());
                mapCanvas.getGraphicsContext2D().fillRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());

                for (int i = tileLayers.length - 1; i > -1; --i) {
                    if (!displayedLayers[i].get()) {
                        continue;
                    }

                    final int[][] layerData = tileLayers[i].getTiles();
                    //TODO: remove tileset name check?
                    if (null == layerData || null == head.getTilesetNames()[i]) {
                        continue;
                        //TODO: do something else?
                    }

                    final PixelReader tilesetReader = tilesetPane.tilesets[i].getPixelReader();
                    if (null == tilesetReader) { //this shouldn't happen anymore since it waits for them to be loaded
                        continue;
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
                    mapCanvas.getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tmpLayerImg, zoom.get()), 0, 0);

                    if (displayTileTypes.get()) {
                        final int[][] attributes = pxAttrs[selectedLayer.get()].getAttributes();

                        if (null != attributes) {
                            for (int y = 0; y < layerData.length; ++y) {
                                for (int x = 0; x < layerData[0].length; ++x) {
                                    final int tilesetX = layerData[y][x] % (TILESET_WIDTH / TILE_WIDTH);
                                    final int tilesetY = layerData[y][x] / (TILESET_WIDTH / TILE_WIDTH);

                                    final int attributesX = attributes[tilesetY][tilesetX] % (PXATTR_IMAGE_WIDTH / PXATTR_TILE_WIDTH);
                                    final int attributesY = attributes[tilesetY][tilesetX] / (PXATTR_IMAGE_HEIGHT / PXATTR_TILE_HEIGHT);

                                    final WritableImage tileTypeImg = new WritableImage(pxAttrImg.getPixelReader(),
                                                                                        attributesX * PXATTR_TILE_WIDTH,
                                                                                        attributesY * PXATTR_TILE_HEIGHT,
                                                                                        PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT);

                                    final int scale = zoom.get() / 2;

                                    mapCanvas.getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tileTypeImg, scale),
                                                                               x * PXATTR_TILE_WIDTH * scale,
                                                                               y * PXATTR_TILE_HEIGHT * scale);
                                }
                            }
                        }
                    }
                }
            }

            /**
             * Fixes the size of the {@code Canvas} and {@code ScrollPane}
             */
            private void fixSize() {
                int maxWidth = 0, maxHeight = 0;
                for (final PxPack.TileLayer layer : tileLayers) {
                    if (null == layer.getTiles()) {
                        continue;
                    }
                    if (layer.getTiles()[0].length > maxWidth) {
                        maxWidth = layer.getTiles()[0].length;
                    }

                    if (layer.getTiles().length > maxHeight) {
                        maxHeight = layer.getTiles().length;
                    }
                }

                mapCanvas.setWidth(maxWidth * TILE_WIDTH * zoom.get());
                mapCanvas.setHeight(maxHeight * TILE_HEIGHT * zoom.get());

                setWidth(mapCanvas.getWidth());
                setHeight(mapCanvas.getHeight());
            }
        }

        private class SettingsPane extends GridPane {
            SettingsPane() {
                setPadding(new Insets(10, 10, 10, 10));
                setVgap(10);
                setHgap(20);

                int x = 0;
                initDisplayedLayersSetting(x++);
                initSelectedLayerSetting(x++);
                initResizeButton(x++);
                initDrawTileTypesSetting(x++);
            }

            private void initDisplayedLayersSetting(final int x) {
                final Text displayedLayersLabel = new Text(Messages.getString("MapEditTab.TileEditTab.DISPLAYED_LAYERS"));
                displayedLayersLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
                add(displayedLayersLabel, x, 0);

                final CheckBox[] displayedLayersCheckboxes = new CheckBox[mapPane.tileLayers.length];
                for (int i = 0; i < displayedLayersCheckboxes.length; ++i) {
                    displayedLayersCheckboxes[i] = new CheckBox(Messages.getString(0 == i ?
                                                                                   "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                          : 1 == i ?
                                                                                            "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                   : "MapEditTab.TileEditTab.Layers.BACKGROUND"));
                    displayedLayersCheckboxes[i].setAllowIndeterminate(false);
                    displayedLayersCheckboxes[i].setSelected(true);
                    displayedLayers[i].bind(displayedLayersCheckboxes[i].selectedProperty());

                    add(displayedLayersCheckboxes[i], x, i + 1);
                }
            }

            private void initSelectedLayerSetting(final int x) {
                final Text selectedLayerLabel = new Text(Messages.getString("MapEditTab.TileEditTab.SELECTED_LAYER"));
                selectedLayerLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
                add(selectedLayerLabel, x, 0);

                final ToggleGroup selectedLayerToggleGroup = new ToggleGroup();
                final RadioButton[] selectedLayerRadioButtons = new RadioButton[mapPane.tileLayers.length];
                for (int i = 0; i < selectedLayerRadioButtons.length; ++i) {
                    selectedLayerRadioButtons[i] = new RadioButton(Messages.getString(0 == i ?
                                                                                      "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                             : 1 == i ?
                                                                                               "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                      : "MapEditTab.TileEditTab.Layers.BACKGROUND"));

                    selectedLayerRadioButtons[i].setToggleGroup(selectedLayerToggleGroup);

                    if (selectedLayer.get() == i) {
                        selectedLayerRadioButtons[i].setSelected(true);
                    }

                    final int layer = i;
                    selectedLayerRadioButtons[i].selectedProperty().addListener(new ChangeListener <Boolean>() {
                        @Override
                        public void changed(final ObservableValue <? extends Boolean> observable, final Boolean oldValue,
                                            final Boolean newValue) {

                            if (newValue) {
                                selectedLayer.set(layer);
                            }
                        }
                    });
                    add(selectedLayerRadioButtons[i], x, i + 1);
                }
            }

            private void initResizeButton(final int x) {
                final Button resizeButton = new Button(Messages.getString("MapEditTab.TileEditTab.Resize.BUTTON_TEXT"));
                resizeButton.setOnAction(new EventHandler <ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent event) {
                        final String title = "Resize " + Messages.getString(0 == selectedLayer.get() ?
                                                                            "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                                     : 1 == selectedLayer.get() ?
                                                                                                       "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                                                : "MapEditTab.TileEditTab.Layers.BACKGROUND");
                        final int width = null == mapPane.tileLayers[selectedLayer.get()].getTiles() ? 0 :
                                          mapPane.tileLayers[selectedLayer.get()].getTiles()[0].length;
                        final int height = null == mapPane.tileLayers[selectedLayer.get()].getTiles() ? 0 :
                                           mapPane.tileLayers[selectedLayer.get()].getTiles().length;

                        final String currentSizeStr = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.CURRENT_SIZE"),
                                                                           width, height);

                        final Optional <Pair <String, String>> result = JavaFXUtil.createDualTextFieldDialog(title, currentSizeStr,
                                                                                                             Messages.getString("MapEditTab.TileEditTab.Resize.NEW_WIDTH"),
                                                                                                             Messages.getString("MapEditTab.TileEditTab.Resize.NEW_HEIGHT"),
                                                                                                             Messages.getString("MapEditTab.TileEditTab.Resize.DIALOG_OK"))
                                                                                  .showAndWait();

                        if (result.isPresent()) {
                            //removes all non-digit characters
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

                                mapPane.tileLayers[selectedLayer.get()].resize(newWidth, newHeight);

                                mapPane.fixSize();
                                mapPane.redrawMap();
                            }
                        }

                    }
                });
                add(resizeButton, x, 0);
            }

            private void initDrawTileTypesSetting(final int x) {
                final CheckBox toggle = new CheckBox(Messages.getString("MapEditTab.TileEditTab.SHOW_TILE_TYPES_TEXT"));
                toggle.setSelected(false);
                displayTileTypes.bind(toggle.selectedProperty());

                add(toggle, x, 0);
            }
        }
    }
}