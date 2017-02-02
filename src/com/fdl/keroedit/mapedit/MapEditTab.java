//TODO: Try using resize() instead of setWidth() and setHeight()
//TODO: Resizing map & tileset is slightly slow...

package com.fdl.keroedit.mapedit;

import java.io.File;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Insets;

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;

import javafx.scene.control.Alert;

import java.util.Optional;

import javafx.util.Pair;

import javafx.scene.control.Dialog;
import javafx.scene.control.ColorPicker;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.scene.input.MouseButton;

import javafx.event.ActionEvent;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;

import javafx.scene.paint.Color;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.image.PixelFormat;

import java.nio.ByteBuffer;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.application.Platform;

import com.fdl.keroedit.util.JavaFXUtil;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.Config;

import com.fdl.keroedit.resource.ResourceManager;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.map.PxPack;
import com.fdl.keroedit.map.PxAttr;

import com.fdl.keroedit.script.ScriptEditTab;

public class MapEditTab extends Tab {
    //Used only in TileEditTab but I wanted it static so there's only one instance of it
    private static final Image pxAttrImg = ResourceManager.getImage("assist/attribute.png");

    private static final SimpleIntegerProperty mapZoom = new SimpleIntegerProperty(Config.mapZoom);
    private static final SimpleIntegerProperty tilesetZoom = new SimpleIntegerProperty(Config.tilesetZoom);
    private static final SimpleObjectProperty <Color> tilesetBgColor = new SimpleObjectProperty <>(Config.tilesetBgColor);

    private static final SimpleBooleanProperty[] displayedLayers = {new SimpleBooleanProperty(true),
                                                                    new SimpleBooleanProperty(true),
                                                                    new SimpleBooleanProperty(true)};
    private static final SimpleIntegerProperty selectedLayer = new SimpleIntegerProperty();
    private static final SimpleBooleanProperty showTileTypes = new SimpleBooleanProperty(false);

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

        final TabPane tPane = new TabPane(new PropertyEditTab(), new TileEditTab(), new ScriptEditTab(map.getName()));
        tPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        setContent(tPane);
    }

    public static void setMapZoom(final int zoom) {
        if (0 >= zoom) {
            throw new IllegalArgumentException(Messages.getString("MapEditTab.ZOOM_EXCEPT"));
        }
        mapZoom.set(zoom);
    }

    public static void setTilesetZoom(final int zoom) {
        if (0 >= zoom) {
            throw new IllegalArgumentException(Messages.getString("MapEditTab.ZOOM_EXCEPT"));
        }
        tilesetZoom.set(zoom);
    }

    public static void setTilesetBgColor(final Color color) {
        tilesetBgColor.set(color);
    }

    public static void bindDisplayedLayer(final int index, final BooleanProperty property) {
        displayedLayers[index].bind(property);
    }

    public static void setSelectedLayer(final int layer) {
        selectedLayer.set(layer);
    }

    public static void bindShowTileTypes(final BooleanProperty property) {
        showTileTypes.bind(property);
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

        private final PxPack.Head head;
        //private final ArrayList<PxPack.Entity> entities;

        private final MapPane mapPane;
        private final TilesetPane tilesetPane;

        TileEditTab() {
            super(Messages.getString("MapEditTab.TileEditTab.TITLE"));

            head = map.getHead(); //TODO: Store same head as properties tab in order to automatically update properties
            //entities = map.getEntities();

            setContent(new SplitPane(mapPane = new MapPane(), tilesetPane = new TilesetPane()));
        }

        private class TilesetPane extends SplitPane {
            private final Canvas tilesetCanvas;
            private final Canvas tileTypeCanvas;
            //private final Image selectedTileRect;

            private final ImageView selectedTileImgView;

            private Image[] tilesets;
            private PxAttr[] pxAttrs; //put this up into TileEditTab?
            private int[] selectedTiles;

            private final Service <Void> redrawTileTypes;
            private final Service <Void> displaySelectedTile;
            private final Service <Void> loadTilesets;
            private final Service <Void> loadPxAttrs;

            TilesetPane() {
                redrawTileTypes = new Service <Void>() {
                    @Override
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                Platform.runLater(() -> tileTypeCanvas.getGraphicsContext2D()
                                                                      .clearRect(0, 0,
                                                                                 tileTypeCanvas.getWidth(),
                                                                                 tileTypeCanvas.getHeight()));

                                final int[][] attributes = pxAttrs[selectedLayer.get()].getAttributes();
                                if (null != attributes) {
                                    final PixelReader pxAttrImgReader = pxAttrImg.getPixelReader();
                                    final WritableImage tmpTileTypeImg = new WritableImage((int)tileTypeCanvas.getWidth() *
                                                                                           (PXATTR_IMAGE_WIDTH / TILESET_WIDTH),
                                                                                           (int)tileTypeCanvas.getHeight() *
                                                                                           (PXATTR_IMAGE_HEIGHT / TILESET_HEIGHT));
                                    final PixelWriter tmpTileTypeImgWriter = tmpTileTypeImg.getPixelWriter();

                                    final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                                    final byte[] attrTile = new byte[PXATTR_TILE_WIDTH * PXATTR_TILE_HEIGHT * 4];
                                    for (int y = 0; y < attributes.length; ++y) {
                                        for (int x = 0; x < attributes[y].length; ++x) {
                                            final int attributesX = attributes[y][x] %
                                                                    (PXATTR_IMAGE_WIDTH / PXATTR_TILE_WIDTH);
                                            final int attributesY = attributes[y][x] /
                                                                    (PXATTR_IMAGE_HEIGHT / PXATTR_TILE_HEIGHT);

                                            pxAttrImgReader.getPixels(attributesX * PXATTR_TILE_WIDTH,
                                                                      attributesY * PXATTR_TILE_HEIGHT,
                                                                      PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT, pxFormat,
                                                                      attrTile, 0, PXATTR_TILE_WIDTH * 4);

                                            tmpTileTypeImgWriter.setPixels(x * PXATTR_TILE_WIDTH,
                                                                           y * PXATTR_TILE_HEIGHT, PXATTR_TILE_WIDTH,
                                                                           PXATTR_TILE_HEIGHT, pxFormat, attrTile, 0,
                                                                           PXATTR_TILE_WIDTH * 4);
                                        }
                                    }

                                    Platform.runLater(() -> tileTypeCanvas.getGraphicsContext2D()
                                                                          .drawImage(JavaFXUtil.scaleImage(tmpTileTypeImg,
                                                                                                           tilesetZoom.get() / 2),
                                                                                     0, 0));
                                }

                                return null;
                            }
                        };
                    }
                };

                displaySelectedTile = new Service <Void>() {
                    @Override
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                final int x = selectedTiles[selectedLayer.get()] % (TILESET_WIDTH / TILE_WIDTH);
                                final int y = selectedTiles[selectedLayer.get()] / (TILESET_HEIGHT / TILE_HEIGHT);

                                final Image tilesetImg = tilesets[selectedLayer.get()];
                                final WritableImage tileImg = 0 < tilesetImg.getWidth() ?
                                                              new WritableImage(tilesetImg.getPixelReader(), x * TILE_WIDTH,
                                                                                y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT) :
                                                              new WritableImage(TILE_WIDTH, TILE_HEIGHT);

                                Platform.runLater(() -> selectedTileImgView
                                        .setImage(JavaFXUtil.scaleImage(tileImg, (int)((TILESET_WIDTH * 2) / tileImg.getWidth()))));

                                return null;
                            }
                        };
                    }
                };

                loadTilesets = new Service <Void>() {
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                final String[] tilesetNames = head.getTilesetNames();
                                tilesets = new Image[tilesetNames.length];

                                for (int i = 0; i < tilesets.length; ++i) {
                                    final Image uncroppedTileset = new Image("file:///" +
                                                                             GameData.getResourceFolder().getAbsolutePath() +
                                                                             File.separatorChar + "img" + File.separatorChar +
                                                                             tilesetNames[i] + ".png");
                                    while (1 != uncroppedTileset.getProgress()) {
                                    }

                                    //TODO: Figure out purpose of the rest of the tileset and see if it's worth storing
                                    //(doesn't look to be right now)
                                    final Image croppedTileset = (0 < uncroppedTileset.getWidth()) ?
                                                                 new WritableImage(uncroppedTileset.getPixelReader(), 0, 0,
                                                                                   TILESET_WIDTH, TILESET_HEIGHT) :
                                                                 uncroppedTileset;

                                    tilesets[i] = croppedTileset;
                                }
                                return null;
                            }
                        };
                    }
                };
                loadTilesets.setOnSucceeded((event) -> {
                    fixSize();
                    setDividerPositions(0.5);

                    redrawTileset();
                    redrawTileTypes.start();

                    displaySelectedTile.start();

                    for (int i = 0; i < mapPane.tileLayers.length; ++i) {
                        mapPane.redrawLayer(i);
                    }
                });

                loadPxAttrs = new Service <Void>() {
                    @Override
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                final String[] tilesetNames = head.getTilesetNames();
                                pxAttrs = new PxAttr[tilesetNames.length];

                                for (int i = 0; i < pxAttrs.length; ++i) {
                                    final File pxAttrFile = new File(GameData.getResourceFolder().getAbsolutePath() +
                                                                     File.separatorChar + "img" + File.separatorChar +
                                                                     tilesetNames[i] + ".pxattr");
                                    try {
                                        pxAttrs[i] = new PxAttr(pxAttrFile);
                                    }
                                    catch (final IOException | ParseException except) {
                                        //TODO: Option to create one? (will have to be all blank...)
                                        Platform.runLater(() -> JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                                                       Messages.getString("MapEditTab.TileEditTab.PxAttrLoadExcept.TITLE"),
                                                                                       null,
                                                                                       except.getMessage()).showAndWait());
                                    }
                                }

                                return null;
                            }
                        };
                    }
                };

                loadTilesets.start();
                loadPxAttrs.start();

                selectedLayer.addListener((observable, oldValue, newValue) -> {
                    redrawTileset();
                    redrawTileTypes.restart();
                    displaySelectedTile.restart();
                });

                tilesetZoom.addListener((observable, oldValue, newValue) -> {
                    fixSize();
                    redrawTileset();
                    redrawTileTypes.restart();
                });

                tilesetBgColor.addListener((observable, oldValue, newValue) -> redrawTileset());

                selectedTiles = new int[PxPack.NUM_LAYERS];
                tilesetCanvas = new Canvas();
                tilesetCanvas.setOnMouseClicked((event) -> {
                    if (event.getButton().equals(MouseButton.PRIMARY)) {
                        if (0 == tilesets[selectedLayer.get()].getWidth() ||
                            0 == tilesets[selectedLayer.get()].getHeight()) {
                            selectedTiles[selectedLayer.get()] = 0;
                            return;
                        }
                        final int x = (int)(event.getX() / tilesetZoom.get() / TILE_WIDTH);
                        final int y = (int)(event.getY() / tilesetZoom.get() / TILE_HEIGHT);
                        final int width = TILESET_WIDTH /* * tilesetZoom.get() / tilesetZoom.get()*/ / TILE_WIDTH;

                        selectedTiles[selectedLayer.get()] = (y * width) + x;

                        displaySelectedTile.restart();
                    }
                });

                tileTypeCanvas = new Canvas();
                tileTypeCanvas.visibleProperty().bind(showTileTypes);
                tileTypeCanvas.setOnMouseClicked(tilesetCanvas::fireEvent);

                final StackPane stackPane = new StackPane(tilesetCanvas, tileTypeCanvas);

                final ScrollPane tilesetScrollPane = new ScrollPane(stackPane);
                tilesetScrollPane.setPannable(true);

                tilesetScrollPane.maxHeightProperty().bind(tilesetCanvas.heightProperty());

                selectedTileImgView = new ImageView();

                getItems().addAll(tilesetScrollPane, new Pane(selectedTileImgView));
                setOrientation(Orientation.VERTICAL);
            }

            private void redrawTileset() {
                final GraphicsContext tilesetGContext = tilesetCanvas.getGraphicsContext2D();

                tilesetGContext.setFill(tilesetBgColor.get());
                tilesetGContext.fillRect(0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());

                tilesetGContext.drawImage(JavaFXUtil.scaleImage(tilesets[selectedLayer.get()],
                                                                tilesetZoom.get()), 0, 0);

                //Since right now I only load the necessary part of the tileset, a simpler drawImage() method can be called (^)
                                /*tilesetGContext.drawImage(JavaFXUtil.scaleImage(tilesets[selectedLayer.get()], zoom.get()),
                                                            0, 0, TILESET_WIDTH * zoom.get(), TILESET_HEIGHT * zoom.get(),
                                                            0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());*/
            }

            private void fixSize() {
                tilesetCanvas.setWidth(TILESET_WIDTH * tilesetZoom.get());
                tilesetCanvas.setHeight(TILESET_HEIGHT * tilesetZoom.get());

                tileTypeCanvas.setWidth(tilesetCanvas.getWidth());
                tileTypeCanvas.setHeight(tilesetCanvas.getHeight());
            }
        }

        private class MapPane extends ScrollPane {
            private final SimpleObjectProperty <Color> bgColor;

            //TODO: Add listener for size for when a layer is resized?
            private final PxPack.TileLayer[] tileLayers;
            private final Canvas[] mapCanvases;
            private final StackPane mapStackPane;

            MapPane() {
                selectedLayer.addListener((observable, oldValue, newValue) -> {
                    if (showTileTypes.get()) {
                        redrawLayer(oldValue.intValue()); //"undraw" tiletypes from previously selected layer
                        redrawLayer(newValue.intValue()); //draw tiletypes onto new selected layer
                    }
                });

                showTileTypes.addListener((observable, oldValue, newValue) -> redrawLayer(selectedLayer.get()));

                tileLayers = map.getTileLayers();

                mapCanvases = new Canvas[tileLayers.length];
                for (int i = 0; i < mapCanvases.length; ++i) {
                    mapCanvases[i] = new Canvas();
                    mapCanvases[i].visibleProperty().bind(displayedLayers[i]);
                }
                mapZoom.addListener((observable, oldValue, newValue) -> {
                    fixSize();
                    for (int i = 0; i < mapCanvases.length; ++i) {
                        redrawLayer(i);
                    }
                });
                fixSize();

                mapStackPane = new StackPane();
                mapStackPane.setAlignment(Pos.TOP_LEFT);
                for (int i = mapCanvases.length - 1; i > -1; --i) {
                    mapStackPane.getChildren().add(mapCanvases[i]);
                }

                mapStackPane.setOnMouseClicked((event) -> {
                    try {
                        new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                if (event.getButton().equals(MouseButton.PRIMARY) &&
                                    null != tileLayers[selectedLayer.get()].getTiles() &&
                                    mapCanvases[selectedLayer.get()].isVisible()) {

                                    final int x = (int)(event.getX() / mapZoom.get() / TILE_WIDTH);
                                    final int y = (int)(event.getY() / mapZoom.get() / TILE_HEIGHT);

                                    if (y < tileLayers[selectedLayer.get()].getTiles().length &&
                                        x < tileLayers[selectedLayer.get()].getTiles()[y].length) {

                                        tileLayers[selectedLayer.get()].setTile(x, y,
                                                                                tilesetPane.selectedTiles[selectedLayer.get()]);
                                        redrawLayer(selectedLayer.get());
                                    }
                                }

                                return null;
                            }
                        }.call();
                    }
                    catch (final Exception exception) {

                    }
                });
                mapStackPane.setOnMouseDragged(mapStackPane.getOnMouseClicked()); //bit slow, especially with tiletypes on...

                bgColor = new SimpleObjectProperty <>(head.getBgColor());
                bgColor.addListener((observable, oldValue, newValue) -> JavaFXUtil.setBackgroundColor(newValue, mapStackPane));
                JavaFXUtil.setBackgroundColor(bgColor.get(), mapStackPane);

                setPannable(false);

                setContent(mapStackPane);
            }

            private void redrawLayer(final int layer) {
                try {
                    new Task <Void>() {
                        @Override
                        protected Void call() throws Exception {
                            final int[][] layerData = tileLayers[layer].getTiles();
                            //TODO: remove tileset name check?
                            if (null == layerData || null == head.getTilesetNames()[layer]) {
                                return null;
                                //TODO: do something else?
                            }

                            final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();
                            if (null == tilesetReader) { //this shouldn't happen anymore since it waits for them to be loaded
                                return null;
                                //TODO: do something else?
                            }

                            final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                            final WritableImage tmpLayerImg = new WritableImage(layerData[0].length * TILE_WIDTH,
                                                                                layerData.length * TILE_HEIGHT);
                            final PixelWriter tmpLayerImgWriter = tmpLayerImg.getPixelWriter();

                            ////////////////////////////////////////////////////////////////////////////////////////////

                            final PixelReader pxAttrImgReader;
                            final WritableImage tmpTileTypeImg;
                            final PixelWriter tmpTileTypeImgWriter;
                            final int[][] attributes;
                            final byte[] attrTile;

                            final boolean dispTileTypes;

                            if (showTileTypes.get() && selectedLayer.get() == layer) {
                                pxAttrImgReader = pxAttrImg.getPixelReader();
                                tmpTileTypeImg = new WritableImage((int)tmpLayerImg.getWidth() * 2,
                                                                   (int)tmpLayerImg.getHeight() * 2);
                                tmpTileTypeImgWriter = tmpTileTypeImg.getPixelWriter();
                                attributes = tilesetPane.pxAttrs[selectedLayer.get()].getAttributes();
                                attrTile = new byte[PXATTR_TILE_WIDTH * PXATTR_TILE_HEIGHT * 4];

                                dispTileTypes = null != attributes;
                            }
                            else {
                                pxAttrImgReader = null;
                                tmpTileTypeImg = null;
                                tmpTileTypeImgWriter = null;
                                attributes = null;
                                attrTile = null;

                                dispTileTypes = false;
                            }

                            ////////////////////////////////////////////////////////////////////////////////////////////

                            final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];

                            for (int y = 0; y < layerData.length; ++y) {
                                for (int x = 0; x < layerData[y].length; ++x) {
                                    final int tilesetX = layerData[y][x] % (TILESET_WIDTH / TILE_WIDTH);
                                    final int tilesetY = layerData[y][x] / (TILESET_HEIGHT / TILE_HEIGHT);

                                    tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT, TILE_WIDTH,
                                                            TILE_HEIGHT, pxFormat, tile, 0, TILE_WIDTH * 4);

                                    tmpLayerImgWriter.setPixels(x * TILE_WIDTH, y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT,
                                                                pxFormat, tile, 0, TILE_WIDTH * 4);

                                    ////////////////////////////////////////////////////////////////////////////////////

                                    if (dispTileTypes) {
                                        final int attributesX = attributes[tilesetY][tilesetX] %
                                                                (PXATTR_IMAGE_WIDTH / PXATTR_TILE_WIDTH);
                                        final int attributesY = attributes[tilesetY][tilesetX] /
                                                                (PXATTR_IMAGE_HEIGHT / PXATTR_TILE_HEIGHT);

                                        pxAttrImgReader.getPixels(attributesX * PXATTR_TILE_WIDTH,
                                                                  attributesY * PXATTR_TILE_HEIGHT,
                                                                  PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT, pxFormat,
                                                                  attrTile, 0, PXATTR_TILE_WIDTH * 4);

                                        tmpTileTypeImgWriter.setPixels(x * PXATTR_TILE_WIDTH,
                                                                       y * PXATTR_TILE_HEIGHT, PXATTR_TILE_WIDTH,
                                                                       PXATTR_TILE_HEIGHT, pxFormat, attrTile, 0,
                                                                       PXATTR_TILE_WIDTH * 4);
                                    }
                                }
                            }

                            Platform.runLater(() -> {
                                mapCanvases[layer].getGraphicsContext2D().clearRect(0, 0, mapCanvases[layer].getWidth(),
                                                                                    mapCanvases[layer].getHeight());

                                mapCanvases[layer].getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tmpLayerImg,
                                                                                                          mapZoom.get()),
                                                                                    0, 0);
                                //null images ignored
                                mapCanvases[layer].getGraphicsContext2D().drawImage(JavaFXUtil.scaleImage(tmpTileTypeImg,
                                                                                                          mapZoom.get() / 2),
                                                                                    0, 0);
                            });

                            return null;
                        }
                    }.call();
                }
                catch (final Exception except) {

                }
            }

            /**
             * Fixes the size of the {@code Canvas}es and {@code ScrollPane}
             */
            private void fixSize() {
                int maxWidth = 0;
                for (int i = 0; i < tileLayers.length; ++i) {
                    if (null == tileLayers[i].getTiles()) {
                        mapCanvases[i].setWidth(0);
                        mapCanvases[i].setHeight(0);
                        continue;
                    }
                    final int width = tileLayers[i].getTiles()[0].length * TILE_WIDTH * mapZoom.get();
                    final int height = tileLayers[i].getTiles().length * TILE_HEIGHT * mapZoom.get();

                    if (width > maxWidth) {
                        maxWidth = width;
                    }

                    mapCanvases[i].setWidth(width);
                    mapCanvases[i].setHeight(height);
                }

                setMaxWidth(maxWidth);
            }
        }

        private class SettingsPane extends GridPane {
            SettingsPane() {
                setPadding(new Insets(10, 10, 10, 10));
                setVgap(10);
                setHgap(20);

                int x = 0;
                final Text mapSettingsLabel = new Text(Messages.getString("MapEditTab.TileEditTab.MAP_SETTINGS"));
                mapSettingsLabel.setFont(Font.font(null, FontWeight.BOLD, 15));
                add(mapSettingsLabel, x, 0);

                initResizeButton(x, 1);
                initBgColorButton(x, 2);
            }

            private void initResizeButton(final int x, final int y) {
                final Button resizeButton = new Button(Messages.getString("MapEditTab.TileEditTab.Resize.BUTTON_TEXT"));
                resizeButton.setOnAction((final ActionEvent event) -> {
                    final String title = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.TITLE"),
                                                              Messages.getString(0 == selectedLayer.get() ?
                                                                                 "MapEditTab.TileEditTab.Layers.FOREGROUND"
                                                                                                          : 1 == selectedLayer.get() ?
                                                                                                            "MapEditTab.TileEditTab.Layers.MIDDLEGROUND"
                                                                                                                                     : "MapEditTab.TileEditTab.Layers.BACKGROUND"));
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
                        final String widthStr = result.get().getKey();//.replaceAll("[^0-9]", "");
                        final String heightStr = result.get().getValue();//.replaceAll("[^0-9]", "");

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
                            mapPane.redrawLayer(selectedLayer.get());
                        }
                    }

                });
                add(resizeButton, x, y);
            }

            private void initBgColorButton(final int x, final int y) {
                final Button bgColorButton = new Button(Messages.getString("MapEditTab.TileEditTab.BgColor.BUTTON_TEXT"));
                bgColorButton.setOnAction((final ActionEvent event) -> {
                    final ColorPicker cPicker = new ColorPicker(mapPane.bgColor.get());
                    cPicker.setOnAction((final ActionEvent ev) -> {
                        if (!cPicker.getValue().isOpaque()) {
                            JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("MapEditTab.TileEditTab.BgColor.OpacityError.TITLE"), null,
                                                   Messages.getString("MapEditTab.TileEditTab.BgColor.OpacityError.MESSAGE"))
                                      .showAndWait();
                            return;
                        }
                        mapPane.bgColor.set(cPicker.getValue());
                    });

                    final Dialog <Void> cPickerDialog = new Dialog <>();
                    cPickerDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    cPickerDialog.getDialogPane().setContent(cPicker);
                    cPickerDialog.showAndWait();

                    //TODO: check opacity
                });
                add(bgColorButton, x, y);
            }
        }
    }
}