/*
 * TODO:
 * Try using resize() instead of setWidth() and setHeight()
 * Resizing map & tileset is slightly slow
 * Detect if tileset is not square
 * For map - draw tile types on separate layer just like with the tileset pane
 * Find workaround for NPE with large Canvas (19tunnel maps in KB); current workaround is minimal map zoom
 * - https://bugs.openjdk.java.net/browse/JDK-8089835
 */

package com.fdl.keroedit.mapedit;

import java.io.File;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;

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


import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

import javafx.scene.control.Alert;

import javafx.scene.control.Dialog;
import javafx.scene.control.ColorPicker;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;

import javafx.scene.input.MouseButton;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import javafx.scene.paint.Color;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.image.PixelFormat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.application.Platform;

import com.fdl.keroedit.util.JavaFXUtil;

import com.fdl.keroedit.util.FileEditTab;
import com.fdl.keroedit.util.UndoableEdit;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.Config;

import com.fdl.keroedit.resource.ResourceManager;

import com.fdl.keroedit.gamedata.GameData;

import com.fdl.keroedit.map.PxPack;

import com.fdl.keroedit.script.ScriptEditTab;

public class MapEditTab extends FileEditTab {
    private static final Image pxAttrImg = ResourceManager.getImage("assist/attribute.png");

    private static final SimpleIntegerProperty mapZoom = new SimpleIntegerProperty(Config.mapZoom);
    private static final SimpleIntegerProperty tilesetZoom = new SimpleIntegerProperty(Config.tilesetZoom);
    private static final SimpleObjectProperty <Color> tilesetBgColor = new SimpleObjectProperty <>(Config.tilesetBgColor);

    private static final SimpleBooleanProperty[] displayedLayers = {new SimpleBooleanProperty(true),
                                                                    new SimpleBooleanProperty(true),
                                                                    new SimpleBooleanProperty(true)};
    private static final SimpleIntegerProperty selectedLayer = new SimpleIntegerProperty();

    //TODO: Single view flags property?
    private static final SimpleBooleanProperty showTileTypes = new SimpleBooleanProperty(false);

    private static final Stage tilesetStage; //TODO: Change to Dialog when adding (not setting) event handlers is allowed
    //(or rework how the stage works)

    static {
        tilesetStage = new Stage();
        tilesetStage.setAlwaysOnTop(true);
        tilesetStage.setTitle(Messages.getString("MapEditTab.TileEditTab.TILESET_WINDOW_TITLE"));
        tilesetStage.setScene(new Scene(new Pane()));

        //redock tileset
        tilesetStage.setOnCloseRequest(event -> {
            tilesetStage.getScene().setRoot(new Pane()); //null not accepted as root
            tilesetStage.close(); //same as hiding
        });
    }

    private final TabPane tabPane;
    private /*final*/ PxPack map;

    public MapEditTab(final String mapFileName) {
        final String fullPath = GameData.getResourceFolder().getAbsolutePath() +
                                File.separatorChar + "field" + File.separatorChar + mapFileName + ".pxpack";
        try {
            map = new PxPack(new File(fullPath));
        }
        catch (final IOException | ParseException except) {
            JavaFXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("MapEditTab.OpenExcept.TITLE"), null,
                                   MessageFormat.format(Messages.getString("MapEditTab.OpenExcept.MESSAGE"), mapFileName,
                                                        except.getMessage())).showAndWait();
            getTabPane().getTabs().remove(this);
        }

        setText(mapFileName);
        setId(mapFileName);
        setTooltip(new Tooltip(fullPath));

        //TODO: Context menu? close and rename

        tabPane = new TabPane(new TileEditTab(this),
                              new ScriptEditTab(new File(GameData.getResourceFolder().getAbsolutePath() +
                                                         File.separatorChar + "text" +
                                                         File.separatorChar + map.getName() + ".pxeve"),
                                                false),
                              new PropertyEditTab());
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        setContent(tabPane);
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
        if (layer < 0 || layer > 2) {
            throw new IllegalArgumentException(Messages.getString("MapEditTab.SET_LAYER_EXCEPTION"));
        }
        selectedLayer.set(layer);
    }

    public static void bindShowTileTypes(final BooleanProperty property) {
        showTileTypes.bind(property);
    }

    @Override
    public void undo() {
        final Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof FileEditTab) {
            ((FileEditTab)selectedTab).undo();
        }
    }

    @Override
    public void redo() {
        final Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof FileEditTab) {
            ((FileEditTab)selectedTab).redo();
        }
    }

    @Override
    public void save() {
        System.out.println("Saved");
        setChanged(false);
        //TODO: save pxpack, pxattr, and script
    }

    private class TileEditTab extends FileEditTab {
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

        //private final MapEditTab parent;

        TileEditTab(final MapEditTab parent) {
            super(Messages.getString("MapEditTab.TileEditTab.TITLE"));
            //this.parent = parent;

            head = map.getHead(); //TODO: Store same head as properties tab in order to automatically update properties
            //entities = map.getEntities();

            final SplitPane sPane = new SplitPane(tilesetPane = new TilesetPane(), mapPane = new MapPane());
            sPane.setOrientation(Orientation.VERTICAL);
            sPane.setDividerPositions(0.1);

            initTilesetStage(sPane, parent);

            setContent(sPane);
        }

        @Override
        public void save() {
            setChanged(false);
            System.out.println("TileEditTab saved");
        }

        /**
         * Adds all the {@code EventHandler}s and other things
         * for setting up the tileset {@code Stage}
         */
        private void initTilesetStage(final SplitPane sPane, final MapEditTab parent) {
            //tilesetPane of TileEditTab in EVERY MapEditTab will be removed when tilesetStage is shown
            tilesetStage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> {
                sPane.getItems().remove(tilesetPane);
                /*
                 * The following is a temporary workaround for a bug where removing a node from a SplitPane does not
                 * immediately (or ever?) set its parent to null. If it is not set to null, an exception will be thrown
                 * when an attempt is made to make the Node (in this case tilesetPane) the root of a Scene. This code
                 * will make a Pane the Node's parent, then remove the Node so that the Node's parent is null and the
                 * Node is ready to be made the root of a Scene.
                 * https://bugs.openjdk.java.net/browse/JDK-8148828
                 * https://bugs.openjdk.java.net/browse/JDK-8132898
                 */
                final Pane tmpPane = new Pane(tilesetPane);
                tmpPane.getChildren().remove(tilesetPane);
            });

            //tilesetPane of TileEditTab in EVERY MapEditTab will be added back to sPane when tilesetStage is hidden
            tilesetStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
                sPane.getItems().add(0, tilesetPane);
                sPane.setDividerPositions(0.1);
            });

            //minimizes/shows tilesetStage depending on if TileEditTab is selected or Properties/Script Tabs are selected
            setOnSelectionChanged(event -> tilesetStage.setIconified(!isSelected()));

            //undock tileset
            tilesetPane.setOnMouseClicked(event -> {
                if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()
                    && !tilesetStage.showingProperty().get()) {
                    tilesetStage.show(); //also runs code of WINDOW_SHOWING EventHandler
                    tilesetStage.getScene().setRoot(tilesetPane);
                }
            });

            //change shown tilesetPane when current map is changed
            parent.setOnSelectionChanged(event -> {
                if (parent.isSelected() && tilesetStage.showingProperty().get() &&
                    tilesetStage.getScene().getRoot() != tilesetPane) {
                    tilesetStage.getScene().setRoot(tilesetPane);
                }
            });

            //TODO: Clear tilesetStage when last MapEditTab is closed
            //TODO: Minimize tilesetStage when focused on non-MapEditTab tab

            //if the tilesetStage is being shown on init, change shown tilesetPane to this one
            if (tilesetStage.showingProperty().get()) {
                sPane.getItems().remove(tilesetPane);

                //see WINDOW_SHOWING EventHandler for why this is necessary
                final Pane tmpPane = new Pane(tilesetPane);
                tmpPane.getChildren().remove(tilesetPane);

                tilesetStage.getScene().setRoot(tilesetPane);
            }
        }

        private class TilesetPane extends SplitPane {
            private final Canvas tilesetCanvas;
            private final Canvas tileTypeCanvas;
            private final ImageView selectedTileImgView;
            //private final Image selectedTileRect;

            private Image[] tilesets;
            private ArrayList <ReadOnlyObjectProperty <PxAttrManager.PxAttr>> pxAttrs; //put this up in TileEditTab?
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

                                final int[][] attributes = pxAttrs.get(selectedLayer.get()).get().getAttributes();
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
                                        .setImage(JavaFXUtil.scaleImage(tileImg, TILESET_WIDTH / TILE_WIDTH / 2)));

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
                                                                             tilesetNames[i] + ".png", false);

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
                loadTilesets.setOnSucceeded(event -> {
                    fixSize();
                    setDividerPositions(0.5);

                    redrawTileset();
                    redrawTileTypes.start();

                    displaySelectedTile.start();

                    for (int i = 0; i < mapPane.tileLayers.length; ++i) {
                        mapPane.redrawLayer(i);
                    }
                });

                pxAttrs = new ArrayList <>(PxPack.NUM_LAYERS);
                loadPxAttrs = new Service <Void>() {
                    @Override
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                final String[] tilesetNames = head.getTilesetNames();
                                pxAttrs.clear();

                                for (final String tilesetName : tilesetNames) {
                                    try {
                                        final ReadOnlyObjectProperty <PxAttrManager.PxAttr> pxAttrProp = PxAttrManager.getPxAttr(tilesetName);
                                        pxAttrProp.addListener((observable, oldValue, newValue) -> {
                                            redrawTileTypes.restart();
                                            mapPane.redrawLayer(selectedLayer.get());
                                        });
                                        pxAttrs.add(pxAttrProp);
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
                tilesetCanvas.setOnMouseClicked(event -> {
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
                tileTypeCanvas.widthProperty().bind(tilesetCanvas.widthProperty());
                tileTypeCanvas.heightProperty().bind(tilesetCanvas.heightProperty());

                tileTypeCanvas.visibleProperty().bind(showTileTypes);
                tileTypeCanvas.setOnMouseClicked(tilesetCanvas::fireEvent);

                final StackPane stackPane = new StackPane(tilesetCanvas, tileTypeCanvas);

                final ScrollPane tilesetScrollPane = new ScrollPane(stackPane);
                tilesetScrollPane.setPannable(true);

                selectedTileImgView = new ImageView();

                getItems().addAll(tilesetScrollPane, new Pane(selectedTileImgView));
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
            }
        }

        private class MapPane extends ScrollPane {
            private final SimpleObjectProperty <Color> bgColor;

            //TODO: Add listener for size for when a layer is resized?
            private final PxPack.TileLayer[] tileLayers;

            private final Canvas[] mapCanvases;
            private final Canvas cursorCanvas;
            private final StackPane mapStackPane;

            MapPane() {
                tileLayers = map.getTileLayers();

                mapCanvases = new Canvas[tileLayers.length];
                for (int i = 0; i < mapCanvases.length; ++i) {
                    mapCanvases[i] = new Canvas();
                    mapCanvases[i].visibleProperty().bind(displayedLayers[i]);
                }

                cursorCanvas = new Canvas();
                cursorCanvas.getGraphicsContext2D().setStroke(Color.WHITE);
                cursorCanvas.getGraphicsContext2D().setLineWidth(2.0);

                fixCanvasSizes();

                mapStackPane = new StackPane();
                mapStackPane.setAlignment(Pos.TOP_LEFT);
                for (int i = mapCanvases.length - 1; i > -1; --i) {
                    mapStackPane.getChildren().add(mapCanvases[i]);
                }
                mapStackPane.getChildren().add(cursorCanvas);

                bgColor = new SimpleObjectProperty <>(head.getBgColor());
                bgColor.addListener((observable, oldValue, newValue) -> JavaFXUtil.setBackgroundColor(newValue, mapStackPane));
                JavaFXUtil.setBackgroundColor(bgColor.get(), mapStackPane);

                bindEventHandlers();
                setPannable(false);
                setContextMenu(initContextMenu());
                setContent(mapStackPane);
            }

            /**
             * Binds {@code EventHandler}s to all the {@code Pane}s and
             * {@code Canvas}es in this {@code ScrollPane}
             */
            private void bindEventHandlers() {
                selectedLayer.addListener((observable, oldValue, newValue) -> {
                    if (showTileTypes.get()) {
                        redrawLayer(oldValue.intValue()); //"undraw" tiletypes from previously selected layer
                        redrawLayer(newValue.intValue()); //draw tiletypes onto new selected layer
                    }
                });

                showTileTypes.addListener((observable, oldValue, newValue) -> redrawLayer(selectedLayer.get()));

                mapZoom.addListener((observable, oldValue, newValue) -> {
                    fixCanvasSizes();
                    for (int i = 0; i < mapCanvases.length; ++i) {
                        redrawLayer(i);
                        //TODO: redraw cursor so it fixes size immediately
                    }
                });

                cursorCanvas.setOnMouseMoved(new EventHandler <MouseEvent>() {
                    private int prevX;
                    private int prevY;

                    @Override
                    public void handle(final MouseEvent event) {
                        final int x = (int)(event.getX() / mapZoom.get() / TILE_WIDTH);
                        final int y = (int)(event.getY() / mapZoom.get() / TILE_HEIGHT);

                        if (x != prevX || y != prevY) {
                            prevX = x;
                            prevY = y;

                            final GraphicsContext cursorGContext = cursorCanvas.getGraphicsContext2D();

                            cursorGContext.clearRect(0, 0, cursorCanvas.getWidth(), cursorCanvas.getHeight());
                            cursorGContext.strokeRoundRect(x * TILE_WIDTH * mapZoom.get(), y * TILE_HEIGHT * mapZoom.get(),
                                                           TILE_WIDTH * mapZoom.get(), TILE_HEIGHT * mapZoom.get(), 10, 10);
                        }

                    }
                });
                cursorCanvas.setOnMouseDragged(cursorCanvas.getOnMouseMoved());

                mapStackPane.setOnMouseClicked(event -> {
                    try {
                        new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                final int layer = selectedLayer.get();

                                if (mapCanvases[layer].isVisible()) {
                                    if (event.getButton().equals(MouseButton.PRIMARY) &&
                                        null != tileLayers[layer].getTiles()) {

                                        final int x = (int)(event.getX() / mapZoom.get() / TILE_WIDTH);
                                        final int y = (int)(event.getY() / mapZoom.get() / TILE_HEIGHT);

                                        final int oldTile = tileLayers[layer].getTiles()[y][x];
                                        final int newTile = tilesetPane.selectedTiles[layer];

                                        if (y < tileLayers[layer].getTiles().length &&
                                            x < tileLayers[layer].getTiles()[y].length &&
                                            oldTile != newTile) {

                                            tileLayers[layer].setTile(x, y, newTile);
                                            redrawTile(layer, x, y);

                                            setChanged(true);

                                            getRedoStack().clear();
                                            getUndoStack().addFirst(new UndoableEdit() {
                                                @Override
                                                public void undo() {
                                                    tileLayers[layer].setTile(x, y, oldTile);
                                                    redrawTile(layer, x, y);
                                                }

                                                @Override
                                                public void redo() {
                                                    tileLayers[layer].setTile(x, y, newTile);
                                                    redrawTile(layer, x, y);
                                                }
                                            });
                                        }
                                    }
                                }
                                return null;
                            }
                        }.call();
                    }
                    catch (final Exception exception) {

                    }
                });
                //TODO: mouse drag has its own separate handler as it will definitely need one
                mapStackPane.setOnMouseDragged(mapStackPane.getOnMouseClicked());
            }

            /**
             * Initializes the {@code ContextMenu} for this {@code ScrollPane}
             *
             * @return The created {@code ContextMenu}
             */
            private ContextMenu initContextMenu() {
                final MenuItem[] menuItems = {new MenuItem(Messages.getString("MapEditTab.TileEditTab.Resize.MENU_TEXT")),
                                              new MenuItem(Messages.getString("MapEditTab.TileEditTab.BgColor.MENU_TEXT"))};

                final EnumMap <MapPaneMenuItems, Integer> mapPaneMenuItems = new EnumMap <>(MapPaneMenuItems.class);
                int i = 0;
                for (final MapPaneMenuItems x : MapPaneMenuItems.values()) {
                    mapPaneMenuItems.put(x, i++);
                }

                menuItems[mapPaneMenuItems.get(MapPaneMenuItems.RESIZE)].setOnAction(event -> {
                    final String layerName;
                    switch (selectedLayer.get()) {
                        case 0:
                            layerName = Messages.getString("MapEditTab.TileEditTab.Layers.FOREGROUND");
                            break;
                        case 1:
                            layerName = Messages.getString("MapEditTab.TileEditTab.Layers.MIDDLEGROUND");
                            break;
                        case 2:
                        default:
                            layerName = Messages.getString("MapEditTab.TileEditTab.Layers.BACKGROUND");
                    }

                    final String title = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.TITLE"),
                                                              layerName);

                    final int width = null == mapPane.tileLayers[selectedLayer.get()].getTiles() ? 0 :
                                      mapPane.tileLayers[selectedLayer.get()].getTiles()[0].length;
                    final int height = null == mapPane.tileLayers[selectedLayer.get()].getTiles() ? 0 :
                                       mapPane.tileLayers[selectedLayer.get()].getTiles().length;

                    final String currentSizeStr = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.CURRENT_SIZE"),
                                                                       width, height);

                    //final Optional <Pair <String, String>> result =
                    //TODO: Check that I did this right
                    JavaFXUtil.createDualTextFieldDialog(title, currentSizeStr,
                                                         Messages.getString("MapEditTab.TileEditTab.Resize.NEW_WIDTH"),
                                                         Messages.getString("MapEditTab.TileEditTab.Resize.NEW_HEIGHT"),
                                                         Messages.getString("MapEditTab.TileEditTab.Resize.DIALOG_OK"))
                              .showAndWait().ifPresent(result -> {
                        final String widthStr = result.x;
                        final String heightStr = result.y;

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
                                                       Messages.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.MESSAGE"))
                                          .showAndWait();
                                return;
                            }

                            tileLayers[selectedLayer.get()].resize(newWidth, newHeight);

                            fixCanvasSizes();
                            redrawLayer(selectedLayer.get());

                            setChanged(true);
                        }
                    });
                });

                menuItems[mapPaneMenuItems.get(MapPaneMenuItems.BG_COLOR)].setOnAction(event -> {
                    final ColorPicker cPicker = new ColorPicker(mapPane.bgColor.get());
                    cPicker.setOnAction(ev -> {
                        if (!cPicker.getValue().isOpaque()) {
                            JavaFXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("MapEditTab.TileEditTab.BgColor.OpacityError.TITLE"),
                                                   null,
                                                   Messages.getString("MapEditTab.TileEditTab.BgColor.OpacityError.MESSAGE"))
                                      .showAndWait();
                        }
                        else {
                            head.setBgColor(cPicker.getValue());
                            mapPane.bgColor.set(cPicker.getValue());
                            setChanged(true);
                        }
                    });

                    final Dialog <Void> cPickerDialog = new Dialog <>();
                    cPickerDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    cPickerDialog.initModality(Modality.WINDOW_MODAL);
                    cPickerDialog.getDialogPane().setContent(cPicker);
                    cPickerDialog.showAndWait();
                });

                return new ContextMenu(menuItems);
            }

            /**
             * Redraws a single tile at the given coordinates on a given layer
             *
             * @param layer The layer the tile to redraw is in
             * @param x The x coordinate of the tile to redraw
             * @param y The y coordinate of the tile to redraw
             */
            private void redrawTile(final int layer, final int x, final int y) {
                try {
                    new Task <Void>() {
                        @Override
                        protected Void call() throws Exception {
                            //TODO: remove tileset name check?
                            if (null == tileLayers[layer].getTiles() || null == head.getTilesetNames()[layer]) {
                                return null;
                            }
                            final int tileIndex = tileLayers[layer].getTiles()[y][x];

                            final Image tileImg;

                            final Image tileTypeImg;
                            final int[][] attributes;
                            final boolean dispTileTypes;

                            if (showTileTypes.get() && selectedLayer.get() == layer) {
                                attributes = tilesetPane.pxAttrs.get(selectedLayer.get()).get().getAttributes();
                                dispTileTypes = null != attributes;
                            }
                            else {
                                attributes = null;
                                dispTileTypes = false;
                            }

                            final int tilesetX = tileIndex % (TILESET_WIDTH / TILE_WIDTH);
                            final int tilesetY = tileIndex / (TILESET_HEIGHT / TILE_HEIGHT);

                            tileImg = JavaFXUtil.scaleImage(new WritableImage(tilesetPane.tilesets[layer].getPixelReader(),
                                                                              tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT,
                                                                              TILE_WIDTH, TILE_HEIGHT),
                                                            mapZoom.get());
                            if (dispTileTypes) {
                                final int attributesX = attributes[tilesetY][tilesetX] %
                                                        (PXATTR_IMAGE_WIDTH / PXATTR_TILE_WIDTH);
                                final int attributesY = attributes[tilesetY][tilesetX] /
                                                        (PXATTR_IMAGE_HEIGHT / PXATTR_TILE_HEIGHT);

                                tileTypeImg = JavaFXUtil.scaleImage(new WritableImage(pxAttrImg.getPixelReader(),
                                                                                      attributesX * PXATTR_TILE_WIDTH,
                                                                                      attributesY * PXATTR_TILE_HEIGHT,
                                                                                      PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT),
                                                                    mapZoom.get() / 2);
                            }
                            else {
                                tileTypeImg = null;
                            }

                            //TODO: Fix misalignment bug
                            Platform.runLater(() -> {
                                final GraphicsContext layerGContext = mapCanvases[layer].getGraphicsContext2D();

                                final int calcX = x * TILE_WIDTH * mapZoom.get();
                                final int calcY = y * TILE_HEIGHT * mapZoom.get();

                                layerGContext.clearRect(calcX, calcY, tileImg.getWidth(), tileImg.getHeight());

                                layerGContext.drawImage(tileImg, calcX, calcY);

                                //null images ignored
                                layerGContext.drawImage(tileTypeImg, calcX, calcY);
                            });

                            return null;
                        }
                    }.call();
                }
                catch (final Exception except) {

                }
            }

            /**
             * Redraws an entire given layer, including tile types if they are set to be visible
             *
             * @param layer The layer to redraw
             */
            private void redrawLayer(final int layer) {
                try {
                    new Task <Void>() {
                        @Override
                        protected Void call() throws Exception {
                            final int[][] layerData = tileLayers[layer].getTiles();
                            //TODO: remove tileset name check?
                            if (null == layerData || null == head.getTilesetNames()[layer]) {
                                return null;
                            }

                            final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                            final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();

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

                                attributes = tilesetPane.pxAttrs.get(selectedLayer.get()).get().getAttributes();
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
                                    ////////////////////////////////////////////////////////////////////////////////////
                                }
                            }

                            Platform.runLater(() -> {
                                final GraphicsContext layerGContext = mapCanvases[layer].getGraphicsContext2D();

                                layerGContext.clearRect(0, 0, mapCanvases[layer].getWidth(), mapCanvases[layer].getHeight());

                                layerGContext.drawImage(JavaFXUtil.scaleImage(tmpLayerImg, mapZoom.get()), 0, 0);
                                //null images ignored
                                layerGContext.drawImage(JavaFXUtil.scaleImage(tmpTileTypeImg, mapZoom.get() / 2), 0, 0);
                            });

                            return null;
                        }
                    }.call();
                }
                catch (final Exception except) {

                }
            }

            /**
             * Fixes the sizes of the {@code Canvas}es
             */
            private void fixCanvasSizes() {
                int maxWidth = 0, maxHeight = 0;
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
                    if (height > maxHeight) {
                        maxHeight = height;
                    }

                    mapCanvases[i].setWidth(width);
                    mapCanvases[i].setHeight(height);
                }

                cursorCanvas.setWidth(maxWidth);
                cursorCanvas.setHeight(maxHeight);
            }
        }
    }

    //TODO: Make this extend FileEditTab?
    private enum MapPaneMenuItems {
        RESIZE,
        BG_COLOR
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
}