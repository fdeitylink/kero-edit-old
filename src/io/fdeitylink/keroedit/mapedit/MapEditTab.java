/*
 * TODO:
 * Try using resize() instead of setWidth() and setHeight()
 * Resizing map & tileset is slightly slow
 * For map - draw tile types on separate layer just like with the tileset pane
 * Find workaround for NPE with large Canvas (19tunnel maps in KB); current workaround is minimal map zoom
 * - https://bugs.openjdk.java.net/browse/JDK-8089835
 * Show error on tileset load for 0 dimension? (only if first tileset?)
 * Make method for redrawing subset/region of map
 * Improve multiple selection in tileset (store startX, startY, and cursorX, cursorY)
 * Use EnumSet for flags
 */

package io.fdeitylink.keroedit.mapedit;

import java.util.ArrayList;
import java.util.EnumMap;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import io.fdeitylink.keroedit.util.SafeOrdinalEnum;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;

import javafx.scene.layout.Pane;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

import javafx.scene.control.ButtonType;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

import javafx.scene.control.Alert;

import javafx.scene.control.Dialog;
import javafx.scene.control.ColorPicker;

import javafx.beans.property.SimpleIntegerProperty;
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
import java.util.EnumSet;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.application.Platform;

import javafx.geometry.Rectangle2D;

import io.fdeitylink.keroedit.util.NullArgumentException;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.util.MathUtil;

import io.fdeitylink.keroedit.util.FXUtil;

import io.fdeitylink.keroedit.Config;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.keroedit.util.UndoableEdit;

import io.fdeitylink.keroedit.gamedata.GameData;

import io.fdeitylink.keroedit.map.PxPack;

import io.fdeitylink.keroedit.image.PxAttrManager;
import io.fdeitylink.keroedit.image.ImageManager;

import io.fdeitylink.keroedit.script.ScriptEditTab;

import static io.fdeitylink.keroedit.image.ImageDimension.TILE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.TILE_HEIGHT;
import static io.fdeitylink.keroedit.image.ImageDimension.TILESET_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.TILESET_HEIGHT;

import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_TILE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_TILE_HEIGHT;
import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_IMAGE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_IMAGE_HEIGHT;

public final class MapEditTab extends FXUtil.FileEditTab {
    private static final SimpleIntegerProperty mapZoom;
    private static final SimpleIntegerProperty tilesetZoom;

    private static final SimpleObjectProperty <Color> tilesetBgColor;

    private static final SimpleObjectProperty <EnumSet <LayerFlag>> displayedLayers;
    private static final SimpleIntegerProperty selectedLayer;

    private static DrawMode drawMode;

    private static final SimpleObjectProperty <EnumSet <ViewFlag>> viewSettings;

    private static Image pxAttrImg;
    private static Image entityImg;

    //TODO: Change to Dialog when adding (not setting) event handlers is allowed (or rework how the stage works)
    private static final Stage tilesetStage;

    static {
        mapZoom = new SimpleIntegerProperty(Config.mapZoom);
        tilesetZoom = new SimpleIntegerProperty(Config.tilesetZoom);
        tilesetBgColor = new SimpleObjectProperty <>(Config.tilesetBgColor);

        displayedLayers = new SimpleObjectProperty <>(Config.displayedLayers);
        selectedLayer = new SimpleIntegerProperty(Config.selectedLayer);

        drawMode = Config.drawMode;

        viewSettings = new SimpleObjectProperty <>(Config.viewSettings);

        final Pane emptyPane = new Pane(); //null not accepted as scene roots, so this is used when tileset stage is not shown

        tilesetStage = new Stage();
        tilesetStage.setAlwaysOnTop(true); //TODO: minimize or hide when KeroEdit program not focused
        tilesetStage.setTitle(Messages.getString("MapEditTab.TileEditTab.TILESET_WINDOW_TITLE"));
        tilesetStage.setScene(new Scene(emptyPane));

        //remove tileset from stage
        tilesetStage.setOnCloseRequest(event -> {
            tilesetStage.getScene().setRoot(emptyPane);
            tilesetStage.close(); //same as hiding
        });
    }

    private final TabPane tabPane;

    private final TileEditTab tileEditTab;
    private final ScriptEditTab scriptEditTab;
    private final PropertyEditTab propertyEditTab;

    private final PxPack map;

    public MapEditTab(final String mapName) throws IOException, ParseException {
        if (null == mapName) {
            throw new NullArgumentException("MapEditTab", "mapName");
        }

        if (!GameData.isInitialized()) {
            throw new IllegalStateException("Attempt to create MapEditTab when GameData has not been properly initialized yet");
        }

        initImages();

        final String fullMapPath = GameData.getResourceFolder().toAbsolutePath().toString() +
                                   File.separatorChar + "field" + File.separatorChar +
                                   mapName + ".pxpack";
        try {
            map = new PxPack(Paths.get(fullMapPath));
        }
        catch (final IOException | ParseException except) {
            final String title;
            final String message;

            if (except instanceof IOException) {
                title = Messages.getString("MapEditTab.OpenIOExcept.TITLE");
                message = MessageFormat.format(Messages.getString("MapEditTab.OpenIOExcept.MESSAGE"),
                                               mapName, except.getMessage());

                Logger.logThrowable(except);
            }
            else {
                title = Messages.getString("MapEditTab.OpenParseExcept.TITLE");
                message = MessageFormat.format(Messages.getString("MapEditTab.OpenParseExcept.MESSAGE"),
                                               mapName, except.getMessage());
            }

            FXUtil.createAlert(Alert.AlertType.ERROR, title, null, message).showAndWait();

            throw except;
        }

        setId(mapName);
        setText(mapName);

        //TODO: ensure updates to description in PropertyEditTab reflected in tooltip
        setTooltip(new Tooltip(fullMapPath + "\n" +
                               Messages.getString("MapEditTab.TOOLTIP_DESCRIPTION_LABEL") + map.getHead().getDescription()));

        tileEditTab = new TileEditTab(this);

        //TODO: Don't fail/escalate except if ScriptEditTab() throws IOException?
        scriptEditTab = new ScriptEditTab(Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                    File.separatorChar + "text" +
                                                    File.separatorChar + map.getName() + ".pxeve"), this);

        propertyEditTab = new PropertyEditTab(this);

        tabPane = new TabPane(tileEditTab, scriptEditTab, propertyEditTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        //TODO: Context menu? rename...

        setContent(tabPane);
    }

    public static void setMapZoom(final int zoom) {
        if (0 >= zoom) {
            throw new IllegalArgumentException("Attempt to set map zoom level to be <= 0 (zoom: " + zoom + ")");
        }
        mapZoom.set(zoom);
    }

    public static void setTilesetZoom(final int zoom) {
        if (0 >= zoom) {
            throw new IllegalArgumentException("Attempt to set tileset zoom level to be <= 0 (zoom: " + zoom + ")");
        }
        tilesetZoom.set(zoom);
    }

    public static void setTilesetBgColor(final Color color) {
        tilesetBgColor.set(color);
    }

    public static void setDisplayedLayers(final EnumSet <LayerFlag> flags) {
        displayedLayers.set(EnumSet.copyOf(flags));
    }

    public static void setSelectedLayer(final int layer) {
        if (layer < 0 || layer > 2) {
            throw new IllegalArgumentException("Attempt to set selected layer to value outside range 0 - 2 (layer: " + layer + ")");
        }
        selectedLayer.set(layer);
    }

    public static void setDrawMode(final DrawMode mode) {
        drawMode = mode;
    }

    public static void setViewSettings(final EnumSet <ViewFlag> flags) {
        viewSettings.set(EnumSet.copyOf(flags));
    }

    @Override
    public void undo() {
        final Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof FXUtil.FileEditTab) {
            ((FXUtil.FileEditTab)selectedTab).undo();
        }
    }

    @Override
    public void redo() {
        final Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab instanceof FXUtil.FileEditTab) {
            ((FXUtil.FileEditTab)selectedTab).redo();
        }
    }

    @Override
    public void save() {
        try {
            map.save();

            /*
             * If an IOException is thrown in ScriptEditTab's save() method,
             * it does not rethrow/escalate the exception. Instead it tells
             * the user with a dialog, and does not call setChanged(false)
             * (and thereby the '*' will remain on the tab's label).
             */
            scriptEditTab.save();

            setChanged(false);
        }
        catch (final IOException except) {
            FXUtil.createAlert(Alert.AlertType.ERROR, Messages.getString("MapEditTab.Save.IOExcept.TITLE"), null,
                               MessageFormat.format(Messages.getString("MapEditTab.Save.IOExcept.MESSAGE"),
                                                    map.getName(), except.getMessage())).showAndWait();
        }
    }

    //Made public so the child ScriptEditTab can call it
    @Override
    public void setChanged(final boolean changed) {
        super.setChanged(changed);
        if (!changed) {
            tileEditTab.setChanged(false);
            scriptEditTab.setChanged(false);
            propertyEditTab.setChanged(false);
        }
    }

    /**
     * Wipes the stored PxAttr attribute and entity images from memory
     */
    public static void wipeImages() {
        pxAttrImg = null;
        entityImg = null;
    }

    /**
     * Initializes the {@code pxAttrImg} and {@code entityImg} variables
     */
    private void initImages() {
        //TODO: Should ImageManager handle pxAttrImg & entityImg?
        if (null == pxAttrImg) {
            final Path attrPath = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                            File.separatorChar + "assist" + File.separatorChar +
                                            "attribute.png");
            if (Files.exists(attrPath)) {
                try {
                    pxAttrImg = new Image(Files.newInputStream(attrPath));
                }
                catch (final IOException except) {
                    pxAttrImg = ResourceManager.getImage("assist/attribute.png");
                }
            }
            else {
                pxAttrImg = ResourceManager.getImage("assist/attribute.png");
            }
        }
        if (null == entityImg) {
            final Path unittypePath = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                File.separatorChar + "assist" + File.separatorChar +
                                                "unittype.png");
            if (Files.exists(unittypePath)) {
                try {
                    entityImg = new Image(Files.newInputStream(unittypePath));
                }
                catch (final IOException except) {
                    entityImg = ResourceManager.getImage("assist/unittype.png");
                }
            }
            else {
                entityImg = ResourceManager.getImage("assist/unittype.png");
            }
        }
    }

    private final class TileEditTab extends FXUtil.FileEditTab {
        private final PxPack.Head head; //ensure updates to head reflected in PropertyEditTab
        private final ArrayList <PxPack.Entity> entities;

        private final MapPane mapPane;
        private final TilesetPane tilesetPane;

        private final int[][][] selectedTiles;

        private final MapEditTab parent;

        TileEditTab(final MapEditTab parent) {
            super(Messages.getString("MapEditTab.TileEditTab.TITLE"));
            this.parent = parent;

            selectedTiles = new int[PxPack.NUM_LAYERS][1][1];

            head = map.getHead(); //TODO: Store same head as properties tab in order to automatically update properties
            entities = map.getEntities();

            //TODO: background on map pane (purple checkerboard?)
            final SplitPane sPane = new SplitPane(tilesetPane = new TilesetPane(), mapPane = new MapPane());
            sPane.setOrientation(Orientation.VERTICAL);
            sPane.setDividerPositions(0.1);

            initTilesetStage(sPane);

            setContent(sPane);
        }

        @Override
        public void save() {
            //does nothing
        }

        //Made public so the parent MapEditTab can call it in save()
        @Override
        public void setChanged(final boolean changed) {
            super.setChanged(changed);
        }

        /**
         * Adds all the {@code EventHandler}s and other things
         * for setting up the tileset {@code Stage}
         *
         * @param sPane The {@code SplitPane} containing the {@code TilesetPane} to be swapped
         * between the main {@code Stage} and a secondary {@code Stage}
         */
        private void initTilesetStage(final SplitPane sPane) {
            //tilesetPane in EVERY MapEditTab will be removed from sPane when tilesetStage is shown
            final EventHandler <? super WindowEvent> beforeShowingEvent = event -> {
                sPane.getItems().remove(tilesetPane);

                /*
                 * The following is a temporary workaround for a bug where removing a node from a SplitPane does not
                 * immediately (or ever?) set its parent to null. If it is not set to null, an exception will be thrown
                 * when an attempt is made to make the Node (in this case tilesetPane) the root of a Scene. This code
                 * will make a Pane the Node's parent, then remove the Node so that the Node's parent is null and the
                 * Node is ready to be made the root of a Scene. The bug should be fixed in Java/JavaFX 9.
                 * https://bugs.openjdk.java.net/browse/JDK-8148828
                 * https://bugs.openjdk.java.net/browse/JDK-8132898
                 */
                final Pane tmpPane = new Pane(tilesetPane);
                tmpPane.getChildren().remove(tilesetPane);

                tilesetStage.getScene().setRoot(tilesetPane);
                tilesetStage.setWidth(tilesetPane.getWidth());
                tilesetStage.setHeight(tilesetPane.getHeight());
            };
            tilesetStage.addEventHandler(WindowEvent.WINDOW_SHOWING, beforeShowingEvent);

            //tilesetPane in EVERY MapEditTab will be added back to sPane when tilesetStage is hidden
            final EventHandler <? super WindowEvent> afterHiddenEvent = event -> {
                //read the static{} block at the top of this file - tilesetStage's root set to empty Pane when closed
                sPane.getItems().add(0, tilesetPane);
                sPane.setDividerPositions(0.1);
            };
            tilesetStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, afterHiddenEvent);

            //shows or minimizes tilesetStage depending on if TileEditTab is selected or Properties/Script Tabs are selected
            setOnSelectionChanged(event -> tilesetStage.setIconified(!isSelected()));

            //undock tileset
            tilesetPane.setOnMouseClicked(event -> {
                if (event.getButton().equals(MouseButton.PRIMARY) && 2 == event.getClickCount()
                    && !tilesetStage.showingProperty().get()) {
                    tilesetStage.show(); //also runs code of WINDOW_SHOWING EventHandler
                }
            });

            //change shown tilesetPane when selected MapEditTab is changed
            parent.setOnSelectionChanged(event -> {
                if (parent.isSelected() && tilesetStage.isShowing() &&
                    tilesetStage.getScene().getRoot() != tilesetPane) {
                    tilesetStage.getScene().setRoot(tilesetPane);
                }
            });

            parent.setOnClosed(event -> {
                tilesetStage.removeEventHandler(WindowEvent.WINDOW_SHOWING, beforeShowingEvent);
                tilesetStage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, afterHiddenEvent);
            });

            //TODO: Clear tilesetStage when last MapEditTab is closed
            //TODO: Minimize tilesetStage when focused on non-MapEditTab tab

            //if the tilesetStage is being shown on init, change shown tilesetPane to this one
            if (tilesetStage.showingProperty().get()) {
                beforeShowingEvent.handle(new WindowEvent(tilesetStage, WindowEvent.WINDOW_SHOWING));
            }
        }

        private class TilesetPane extends SplitPane {
            private final Canvas tilesetCanvas;
            private final Canvas tileTypeCanvas;
            private final Canvas selectedRectCanvas;
            private final ImageView selectedTilesImgView;

            private final Rectangle2D[] selectedRegions;

            private Image[] tilesets;
            private ArrayList <ReadOnlyObjectProperty <PxAttrManager.PxAttr>> pxAttrs;

            private Service <Void> redrawTileTypes;
            private Service <Void> drawSelectedTiles;
            private Service <Void> loadTilesets;
            private Service <Void> loadPxAttrs;

            TilesetPane() {
                initServices();

                loadTilesets.start();
                loadPxAttrs.start();

                tilesetCanvas = new Canvas();

                tileTypeCanvas = new Canvas();
                tileTypeCanvas.widthProperty().bind(tilesetCanvas.widthProperty());
                tileTypeCanvas.heightProperty().bind(tilesetCanvas.heightProperty());

                tileTypeCanvas.setVisible(viewSettings.get().contains(ViewFlag.TILE_TYPES));
                tileTypeCanvas.setOnMouseClicked(tilesetCanvas::fireEvent);

                selectedRectCanvas = new Canvas();
                selectedRectCanvas.widthProperty().bind(tilesetCanvas.widthProperty());
                selectedRectCanvas.heightProperty().bind(tilesetCanvas.heightProperty());

                final GraphicsContext selectedRectGContext = selectedRectCanvas.getGraphicsContext2D();
                selectedRectGContext.setStroke(Color.WHITE);
                selectedRectGContext.setLineWidth(2.0);

                selectedRegions = new Rectangle2D[PxPack.NUM_LAYERS];
                for (int i = 0; i < selectedRegions.length; ++i) {
                    selectedRegions[i] = new Rectangle2D(0, 0, 1, 1);
                }
                redrawSelectedRect();

                final StackPane stackPane = new StackPane(tilesetCanvas, tileTypeCanvas, selectedRectCanvas);

                final ScrollPane tilesetScrollPane = new ScrollPane(stackPane);
                tilesetScrollPane.setPannable(false);

                selectedTilesImgView = new ImageView();

                initEventHandlers();

                getItems().addAll(tilesetScrollPane, new Pane(selectedTilesImgView));
            }

            private void initServices() {
                redrawTileTypes = new Service <Void>() {
                    @Override
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                Platform.runLater(() -> tileTypeCanvas
                                        .getGraphicsContext2D().clearRect(0, 0,
                                                                          tileTypeCanvas.getWidth(),
                                                                          tileTypeCanvas.getHeight()));

                                final int[][] attributes = pxAttrs.get(selectedLayer.get()).get().getAttributes();
                                final PixelReader pxAttrImgReader = pxAttrImg.getPixelReader();
                                if (null != attributes && null != pxAttrImgReader) {
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
                                                                          .drawImage(FXUtil.scaleImage(tmpTileTypeImg,
                                                                                                       tilesetZoom.get() / 2),
                                                                                     0, 0));
                                }

                                return null;
                            }
                        };
                    }
                };

                drawSelectedTiles = new Service <Void>() {
                    @Override
                    protected Task <Void> createTask() {
                        return new Task <Void>() {
                            @Override
                            protected Void call() throws Exception {
                                final int layer = selectedLayer.get();

                                final int[][] selectedTilesRect = selectedTiles[layer];

                                final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                                final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();

                                final WritableImage tilesImg = new WritableImage(selectedTilesRect[0].length * TILE_WIDTH,
                                                                                 selectedTilesRect.length * TILE_HEIGHT);
                                final PixelWriter tilesImgWriter = tilesImg.getPixelWriter();

                                final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];

                                for (int y = 0; y < selectedTilesRect.length; ++y) {
                                    for (int x = 0; x < selectedTilesRect[y].length; ++x) {
                                        final int tilesetX = selectedTilesRect[y][x] % (TILESET_WIDTH / TILE_WIDTH);
                                        final int tilesetY = selectedTilesRect[y][x] / (TILESET_HEIGHT / TILE_HEIGHT);

                                        tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT, TILE_WIDTH,
                                                                TILE_HEIGHT, pxFormat, tile, 0, TILE_WIDTH * 4);

                                        tilesImgWriter.setPixels(x * TILE_WIDTH, y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT,
                                                                 pxFormat, tile, 0, TILE_WIDTH * 4);
                                    }
                                }

                                //TODO: Change selectedTiles view to a Canvas?
                                Platform.runLater(() -> selectedTilesImgView
                                        .setImage(FXUtil.scaleImage(tilesImg, tilesetZoom.get())));
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
                                    tilesets[i] = ImageManager.getImage(tilesetNames[i], true);
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
                    redrawTileTypes.start(); //move into if block below?

                    drawSelectedTiles.start();

                    //when both of the Services are complete, redraw layers
                    if (!loadPxAttrs.isRunning()) {
                        mapPane.redrawAllLayers();
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
                                        final ReadOnlyObjectProperty <PxAttrManager.PxAttr> pxAttrProp =
                                                PxAttrManager.getPxAttr(tilesetName);
                                        pxAttrProp.addListener((observable, oldValue, newValue) -> {
                                            redrawTileTypes.restart();
                                            mapPane.redrawLayer(selectedLayer.get());
                                        });
                                        pxAttrs.add(pxAttrProp);
                                    }
                                    catch (final IOException | ParseException except) {
                                        final String title = except instanceof IOException ?
                                                             Messages.getString("MapEditTab.TileEditTab.PxAttrLoadIOExcept.TITLE") :
                                                             Messages.getString("MapEditTab.TileEditTab.PxAttrLoadParseExcept.TITLE");
                                        //TODO: Option to create one? (will have to be all blank...)
                                        Platform.runLater(() -> FXUtil.createAlert(Alert.AlertType.ERROR, title, null,
                                                                                   except.getMessage())
                                                                      .showAndWait());
                                    }
                                }

                                return null;
                            }
                        };
                    }
                };
                loadPxAttrs.setOnSucceeded(event -> {
                    //when both of the Services are complete, redraw layers
                    if (!loadTilesets.isRunning()) {
                        mapPane.redrawAllLayers();
                    }
                });
            }

            private void initEventHandlers() {
                viewSettings.addListener((observable, oldValue, newValue) -> {
                    tileTypeCanvas.setVisible(newValue.contains(ViewFlag.TILE_TYPES));
                });

                selectedLayer.addListener((observable, oldValue, newValue) -> {
                    redrawTileTypes.restart(); //do this before tileset redraw for speed
                    redrawTileset();
                    redrawSelectedRect();
                    drawSelectedTiles.restart();
                });

                tilesetZoom.addListener((observable, oldValue, newValue) -> {
                    fixSize();
                    redrawTileTypes.restart(); //do this before tileset redraw for speed
                    redrawTileset();
                    redrawSelectedRect();
                    drawSelectedTiles.restart();
                });

                tilesetBgColor.addListener((observable, oldValue, newValue) -> redrawTileset());

                tilesetCanvas.setOnMousePressed(event -> {
                    if (event.getButton().equals(MouseButton.PRIMARY)) {
                        if (0 == tilesets[selectedLayer.get()].getWidth() ||
                            0 == tilesets[selectedLayer.get()].getHeight()) {
                            selectedTiles[selectedLayer.get()] = new int[][]{{0}}; //TODO: make array empty?
                            redrawSelectedRect();
                            return;
                        }

                        final int x = (int)(event.getX() / tilesetZoom.get() / TILE_WIDTH);
                        final int y = (int)(event.getY() / tilesetZoom.get() / TILE_HEIGHT);
                        final int width = TILESET_WIDTH / TILE_WIDTH;

                        selectedTiles[selectedLayer.get()] = new int[][]{{(y * width) + x}};

                        selectedRegions[selectedLayer.get()] = new Rectangle2D(x, y, 1, 1);

                        drawSelectedTiles.restart();
                        redrawSelectedRect();
                    }
                });

                tilesetCanvas.setOnMouseDragged(new EventHandler <MouseEvent>() {
                    private int prevX;
                    private int prevY;

                    @Override
                    public void handle(final MouseEvent event) {
                        if (event.getButton().equals(MouseButton.PRIMARY)) {
                            //grabs x & y and bounds them to be within tileset
                            final int x = MathUtil.bound((int)event.getX(), 0, (int)(tilesetCanvas.getWidth() - 1)) /
                                          tilesetZoom.get() / TILE_WIDTH;
                            final int y = MathUtil.bound((int)event.getY(), 0, (int)(tilesetCanvas.getHeight() - 1)) /
                                          tilesetZoom.get() / TILE_HEIGHT;

                            if (x != prevX || y != prevY) {
                                prevX = x;
                                prevY = y;

                                if (0 == tilesets[selectedLayer.get()].getWidth() ||
                                    0 == tilesets[selectedLayer.get()].getHeight()) {
                                    selectedRegions[selectedLayer.get()] = new Rectangle2D(0, 0, 1, 1);
                                    selectedTiles[selectedLayer.get()] = new int[][]{{0}}; //TODO: make array empty? null?
                                    redrawSelectedRect();
                                    return;
                                }

                                final Rectangle2D selectedRect = selectedRegions[selectedLayer.get()];

                                final int dx = (int)(x - (selectedRect.getMaxX() - 1));
                                final int dy = (int)(y - (selectedRect.getMaxY() - 1));

                                //TODO: Make dragging into relative negatives work better
                                //maybe need startX, startY variables on drag start
                                int minX = (int)selectedRect.getMinX();
                                int minY = (int)selectedRect.getMinY();
                                int width = (int)(selectedRect.getWidth() + dx);
                                int height = (int)(selectedRect.getHeight() + dy);

                                if (x < minX) {
                                    minX = x;
                                    width = (int)(selectedRect.getMaxX() - x);
                                }
                                if (y < minY) {
                                    minY = y;
                                    height = (int)(selectedRect.getMaxY() - y);
                                }

                                selectedRegions[selectedLayer.get()] = new Rectangle2D(minX, minY, width, height);

                                final int[][] tiles = new int[height][width];
                                final int tilesetWidth = TILESET_WIDTH / TILE_WIDTH;

                                //copies selected tiles into tiles 2D arr (created ^)
                                for (int tilesY = minY; tilesY < minY + height; ++tilesY) {
                                    for (int tilesX = minX; tilesX < minX + width; ++tilesX) {
                                        tiles[tilesY - minY][tilesX - minX] = (tilesY * tilesetWidth) + tilesX;
                                    }
                                }
                                selectedTiles[selectedLayer.get()] = tiles; //applies selection changes
                                drawSelectedTiles.restart();

                                redrawSelectedRect();
                            }
                        }
                    }
                });

                selectedRectCanvas.setOnMousePressed(tilesetCanvas::fireEvent);
                selectedRectCanvas.setOnMouseDragged(tilesetCanvas::fireEvent);
            }

            private void redrawTileset() {
                final GraphicsContext tilesetGContext = tilesetCanvas.getGraphicsContext2D();

                tilesetGContext.setFill(tilesetBgColor.get());
                tilesetGContext.fillRect(0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());

                tilesetGContext.drawImage(FXUtil.scaleImage(tilesets[selectedLayer.get()], tilesetZoom.get()), 0, 0);

                //Since right now I only load the necessary part of the tileset, a simpler drawImage() method can be called (^)
                /*tilesetGContext.drawImage(FXUtil.scaleImage(tilesets[selectedLayer.get()], zoom.get()),
                                            0, 0, TILESET_WIDTH * zoom.get(), TILESET_HEIGHT * zoom.get(),
                                            0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());*/
            }

            private void redrawSelectedRect() {
                final GraphicsContext selectedRectGContext = selectedRectCanvas.getGraphicsContext2D();
                selectedRectGContext.clearRect(0, 0, selectedRectCanvas.getWidth(), selectedRectCanvas.getHeight());

                final Rectangle2D selectedRect = selectedRegions[selectedLayer.get()];
                selectedRectGContext.strokeRoundRect(selectedRect.getMinX() * TILE_WIDTH * tilesetZoom.get(),
                                                     selectedRect.getMinY() * TILE_HEIGHT * tilesetZoom.get(),
                                                     TILE_WIDTH * selectedRect.getWidth() * tilesetZoom.get(),
                                                     TILE_HEIGHT * selectedRect.getHeight() * tilesetZoom.get(),
                                                     10, 10);
            }

            private void fixSize() {
                tilesetCanvas.setWidth(TILESET_WIDTH * tilesetZoom.get());
                tilesetCanvas.setHeight(TILESET_HEIGHT * tilesetZoom.get());
            }
        }

        private class MapPane extends ScrollPane {
            private final SimpleObjectProperty <Color> bgColor;

            private final PxPack.TileLayer[] tileLayers;

            private final Canvas[] mapCanvases;
            //private final Canvas tileTypesCanvas;
            private final Canvas entityCanvas;
            private final Canvas gridCanvas;
            private final Canvas cursorCanvas;
            private final StackPane mapStackPane;

            MapPane() {
                tileLayers = map.getTileLayers();

                mapCanvases = new Canvas[tileLayers.length];
                for (int i = 0; i < mapCanvases.length; ++i) {
                    mapCanvases[i] = new Canvas();
                    mapCanvases[i].setVisible(displayedLayers.get().contains(LayerFlag.values()[i]));
                }

                entityCanvas = new Canvas();

                gridCanvas = new Canvas();
                gridCanvas.setVisible(viewSettings.get().contains(ViewFlag.GRID));

                cursorCanvas = new Canvas();
                cursorCanvas.getGraphicsContext2D().setStroke(Color.WHITE);
                cursorCanvas.getGraphicsContext2D().setLineWidth(2.0);

                fixCanvasSizes();
                redrawGrid();

                mapStackPane = new StackPane();
                mapStackPane.setAlignment(Pos.TOP_LEFT);
                for (int i = mapCanvases.length - 1; i >= 0; --i) {
                    mapStackPane.getChildren().add(mapCanvases[i]);
                }
                mapStackPane.getChildren().addAll(entityCanvas, gridCanvas, cursorCanvas);

                bgColor = new SimpleObjectProperty <>(head.getBgColor());
                bgColor.addListener((observable, oldValue, newValue) -> FXUtil.setBackgroundColor(mapStackPane, newValue));
                FXUtil.setBackgroundColor(mapStackPane, bgColor.get());

                initEventHandlers();

                setPannable(false);
                setContextMenu(initContextMenu());
                setContent(mapStackPane);

                //redrawEntities();
            }

            /**
             * Binds and initializes {@code EventHandlers} for clicks
             * on the {@code Canvases} and {@code Panes} as well as
             * changes to the various static SimpleIntegerProperties
             * initialized and controlled by the MapEditTab class.
             */
            private void initEventHandlers() {
                displayedLayers.addListener((observable, oldValue, newValue) -> {
                    for (int i = 0; i < LayerFlag.values().length; ++i) {
                        mapCanvases[i].setVisible(newValue.contains(LayerFlag.values()[i]));
                    }
                });

                selectedLayer.addListener((observable, oldValue, newValue) -> {
                    if (viewSettings.get().contains(ViewFlag.TILE_TYPES)) {
                        redrawLayer(oldValue.intValue()); //"undraw" tiletypes from previously selected layer
                        redrawLayer(newValue.intValue()); //draw tiletypes onto new selected layer
                    }
                });

                viewSettings.addListener((observable, oldValue, newValue) -> {
                    //Only one flag is changed at a time
                    //TODO: Find more efficient method (complement?)
                    if ((oldValue.contains(ViewFlag.TILE_TYPES) && !newValue.contains(ViewFlag.TILE_TYPES)) ||
                        (!oldValue.contains(ViewFlag.TILE_TYPES) && newValue.contains(ViewFlag.TILE_TYPES))) {
                        redrawLayer(selectedLayer.get());
                    }
                    else if ((oldValue.contains(ViewFlag.GRID) && !newValue.contains(ViewFlag.GRID)) ||
                             (!oldValue.contains(ViewFlag.GRID) && newValue.contains(ViewFlag.GRID))) {
                        gridCanvas.setVisible(newValue.contains(ViewFlag.GRID));
                    }
                });

                mapZoom.addListener((observable, oldValue, newValue) -> {
                    //TODO: redraw cursor so it fixes size immediately
                    fixCanvasSizes();
                    redrawAllLayers();
                    redrawGrid();
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
                            switch (drawMode) {
                                case DRAW:
                                    final int[][] tilesRect = selectedTiles[selectedLayer.get()];
                                    cursorGContext.strokeRoundRect(x * TILE_WIDTH * mapZoom.get(),
                                                                   y * TILE_HEIGHT * mapZoom.get(),
                                                                   TILE_WIDTH * tilesRect[0].length * mapZoom.get(),
                                                                   TILE_HEIGHT * tilesRect.length * mapZoom.get(),
                                                                   10, 10);
                                    break;
                            }
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

                                if (mapCanvases[layer].isVisible() &&
                                    event.getButton().equals(MouseButton.PRIMARY)) {
                                    final int[][] tiles = tileLayers[layer].getTiles();

                                    if (null != tiles) {
                                        //grabs x & y and bounds them to be within map
                                        final int x = MathUtil.bound((int)event.getX(), 0,
                                                                     (int)(mapCanvases[layer].getWidth() - 1)) /
                                                      mapZoom.get() / TILE_WIDTH;
                                        final int y = MathUtil.bound((int)event.getY(), 0,
                                                                     (int)(mapCanvases[layer].getHeight() - 1)) /
                                                      mapZoom.get() / TILE_HEIGHT;

                                        /*
                                         * Caps newTiles.length in the event that x or y is close enough
                                         * to the map edge that selectedTiles goes off the edge
                                         */
                                        final int hLen = Math.min(tiles[0].length - x, selectedTiles[layer][0].length);
                                        final int vLen = Math.min(tiles.length - y, selectedTiles[layer].length);

                                        final int[][] newTiles = new int[vLen][hLen];

                                        //newTiles potentially smaller in either dimension than selectedTiles, as per hLen & vLen
                                        for (int r = 0; r < newTiles.length; ++r) {
                                            System.arraycopy(selectedTiles[layer][r], 0, newTiles[r], 0, newTiles[r].length);
                                        }

                                        final int[][] oldTiles = new int[newTiles.length][newTiles[0].length];

                                        boolean oldEqualsNew = true;

                                        for (int r = y; r < y + newTiles.length /*&& r < tiles.length*/; ++r) {
                                            //oldTiles stores only the tiles being replaced, a subset of the whole map
                                            System.arraycopy(tiles[r], x, oldTiles[r - y], 0, oldTiles[r - y].length);

                                            for (int c = x; c < x + newTiles[r - y].length /*&& c < tiles[r].length*/; ++c) {
                                                if (oldTiles[r - y][c - x] != newTiles[r - y][c - x]) {
                                                    oldEqualsNew = false;
                                                    tileLayers[layer].setTile(c, r, newTiles[r - y][c - x]);
                                                    redrawTile(layer, c, r);
                                                }
                                            }
                                        }

                                        if (!oldEqualsNew/*Arrays.deepEquals(oldTiles, newTiles)*/) {
                                            setChanged(true);
                                            parent.setChanged(true);

                                            getRedoStack().clear();
                                            getUndoStack().addFirst(new UndoableMapDrawEdit(layer, x, y, oldTiles, newTiles));
                                        }
                                    }
                                }
                                return null;
                            }
                        }.call();
                    }
                    catch (final Exception except) {
                        Logger.logThrowable("Exception in mapStackPane.setOnMouseClicked()", except);
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

                menuItems[MapPaneMenuItem.ordinalMap.get(MapPaneMenuItem.RESIZE)].setOnAction(event -> {
                    final int layer = selectedLayer.get();

                    final String layerName;
                    switch (layer) {
                        case 0:
                            layerName = Messages.getString("PxPack.LayerNames.FOREGROUND");
                            break;
                        case 1:
                            layerName = Messages.getString("PxPack.LayerNames.MIDDLEGROUND");
                            break;
                        case 2:
                        default:
                            layerName = Messages.getString("PxPack.LayerNames.BACKGROUND");
                    }

                    final String title = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.TITLE"),
                                                              layerName);

                    final int[][] oldTiles = mapPane.tileLayers[layer].getTiles();
                    final int oldWidth = null == oldTiles ? 0 : oldTiles[0].length;
                    final int oldHeight = null == oldTiles ? 0 : oldTiles.length;

                    final String currentSizeStr = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.CURRENT_SIZE"),
                                                                       oldWidth, oldHeight);

                    FXUtil.createDualTextFieldDialog(title, currentSizeStr,
                                                     Messages.getString("MapEditTab.TileEditTab.Resize.NEW_WIDTH"),
                                                     Messages.getString("MapEditTab.TileEditTab.Resize.NEW_HEIGHT"))
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
                                FXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("MapEditTab.TileEditTab.Resize.NumFormatExcept.TITLE"),
                                                   null,
                                                   Messages.getString("MapEditTab.TileEditTab.Resize.NumFormatExcept.MESSAGE"))
                                      .showAndWait();
                                return;
                            }

                            if (newWidth > 0xFFFF || newHeight > 0xFFFF) {
                                FXUtil.createAlert(Alert.AlertType.ERROR,
                                                   Messages.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.TITLE"),
                                                   null,
                                                   Messages.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.MESSAGE"))
                                      .showAndWait();
                                return;
                            }

                            tileLayers[layer].resize(newWidth, newHeight);

                            fixCanvasSizes();
                            redrawLayer(layer);

                            setChanged(true);
                            parent.setChanged(true);

                            getRedoStack().clear();
                            getUndoStack().addFirst(new UndoableMapResizeEdit(layer, oldTiles, tileLayers[layer].getTiles()));
                        }
                    });
                });

                menuItems[MapPaneMenuItem.ordinalMap.get(MapPaneMenuItem.BG_COLOR)].setOnAction(event -> {
                    final ColorPicker cPicker = new ColorPicker(mapPane.bgColor.get());
                    cPicker.setOnAction(ev -> {
                        if (!cPicker.getValue().isOpaque()) {
                            FXUtil.createAlert(Alert.AlertType.ERROR,
                                               Messages.getString("MapEditTab.TileEditTab.BgColor.OpacityError.TITLE"),
                                               null,
                                               Messages.getString("MapEditTab.TileEditTab.BgColor.OpacityError.MESSAGE"))
                                  .showAndWait();
                        }
                        else {
                            head.setBgColor(cPicker.getValue());
                            mapPane.bgColor.set(cPicker.getValue());
                            setChanged(true);
                            parent.setChanged(true);
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
             * Redraws a single tile at the given coordinates on a given layer.
             * Also draws its tile type if those are set to be visible.
             *
             * @param layer The layer the tile to redraw is in
             * @param x The x coordinate of the tile to redraw
             * @param y The y coordinate of the tile to redraw
             */
            private void redrawTile(final int layer, final int x, final int y) {
                try {
                    //TODO: Create new Service subclass that takes layer, x, and y params in constructor and can be restarted on demand?
                    new Task <Void>() {
                        @Override
                        protected Void call() throws Exception {
                            final int[][] tiles = tileLayers[layer].getTiles();
                            if (null == tiles) {
                                return null;
                            }

                            final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();
                            if (null == tilesetReader) { //this should handle empty/nonexistent images
                                return null;
                            }

                            final int tileIndex = tiles[y][x];

                            final Image tileImg;

                            final Image tileTypeImg;
                            final int[][] attributes;
                            final boolean drawTileType;

                            if (viewSettings.get().contains(ViewFlag.TILE_TYPES) &&
                                selectedLayer.get() == layer) {
                                attributes = tilesetPane.pxAttrs.get(selectedLayer.get()).get().getAttributes();
                                drawTileType = null != attributes;
                            }
                            else {
                                attributes = null;
                                drawTileType = false;
                            }

                            final int tilesetX = tileIndex % (TILESET_WIDTH / TILE_WIDTH);
                            final int tilesetY = tileIndex / (TILESET_HEIGHT / TILE_HEIGHT);

                            tileImg = FXUtil.scaleImage(new WritableImage(tilesetReader,
                                                                          tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT,
                                                                          TILE_WIDTH, TILE_HEIGHT), mapZoom.get());
                            if (drawTileType) {
                                final int attributesX = attributes[tilesetY][tilesetX] %
                                                        (PXATTR_IMAGE_WIDTH / PXATTR_TILE_WIDTH);
                                final int attributesY = attributes[tilesetY][tilesetX] /
                                                        (PXATTR_IMAGE_HEIGHT / PXATTR_TILE_HEIGHT);

                                tileTypeImg = FXUtil.scaleImage(new WritableImage(pxAttrImg.getPixelReader(),
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
                    Logger.logThrowable("Exception in redrawTile(" + layer + ", " + x + ", " + y + ", " + ")", except);
                }
            }

            /**
             * Redraws an entire given layer, including tile types if they are set to be visible.
             *
             * @param layer The layer to redraw
             */
            private void redrawLayer(final int layer) {
                try {
                    //TODO: Create new Service subclass that takes layer param in constructor and can be restarted on demand?
                    new Task <Void>() {
                        @Override
                        protected Void call() throws Exception {
                            final int[][] tiles = tileLayers[layer].getTiles();
                            if (null == tiles) {
                                return null;
                            }

                            final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();
                            if (null == tilesetReader) { //this should handle empty/nonexistent images
                                return null;
                            }

                            final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                            final WritableImage tmpLayerImg = new WritableImage(tiles[0].length * TILE_WIDTH,
                                                                                tiles.length * TILE_HEIGHT);
                            final PixelWriter tmpLayerImgWriter = tmpLayerImg.getPixelWriter();

                            ////////////////////////////////////////////////////////////////////////////////////////////
                            PixelReader pxAttrImgReader = null;
                            WritableImage tmpTileTypeImg = null;
                            PixelWriter tmpTileTypeImgWriter = null;
                            int[][] attributes = null;
                            byte[] attrTile = null;

                            boolean drawTileTypes = false;

                            if (viewSettings.get().contains(ViewFlag.TILE_TYPES) &&
                                selectedLayer.get() == layer) {
                                attributes = tilesetPane.pxAttrs.get(selectedLayer.get()).get().getAttributes();

                                if (drawTileTypes = (null != attributes)) {
                                    pxAttrImgReader = pxAttrImg.getPixelReader();
                                    tmpTileTypeImg = new WritableImage((int)tmpLayerImg.getWidth() * 2,
                                                                       (int)tmpLayerImg.getHeight() * 2);
                                    tmpTileTypeImgWriter = tmpTileTypeImg.getPixelWriter();

                                    attrTile = new byte[PXATTR_TILE_WIDTH * PXATTR_TILE_HEIGHT * 4];
                                }
                                else {
                                    tmpTileTypeImg = null;
                                }
                            }

                            /*if (!drawTileTypes) {
                                pxAttrImgReader = null;
                                tmpTileTypeImg = null;
                                tmpTileTypeImgWriter = null;
                                attributes = null;
                                attrTile = null;
                            }*/
                            ////////////////////////////////////////////////////////////////////////////////////////////

                            final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];

                            for (int y = 0; y < tiles.length; ++y) {
                                for (int x = 0; x < tiles[y].length; ++x) {
                                    final int tilesetX = tiles[y][x] % (TILESET_WIDTH / TILE_WIDTH);
                                    final int tilesetY = tiles[y][x] / (TILESET_HEIGHT / TILE_HEIGHT);

                                    tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT, TILE_WIDTH,
                                                            TILE_HEIGHT, pxFormat, tile, 0, TILE_WIDTH * 4);

                                    tmpLayerImgWriter.setPixels(x * TILE_WIDTH, y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT,
                                                                pxFormat, tile, 0, TILE_WIDTH * 4);

                                    ////////////////////////////////////////////////////////////////////////////////////
                                    if (drawTileTypes) {
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

                            final Image typeImg = FXUtil.scaleImage(tmpTileTypeImg, mapZoom.get() / 2);
                            Platform.runLater(() -> {
                                final GraphicsContext layerGContext = mapCanvases[layer].getGraphicsContext2D();

                                layerGContext.clearRect(0, 0, mapCanvases[layer].getWidth(), mapCanvases[layer].getHeight());

                                layerGContext.drawImage(FXUtil.scaleImage(tmpLayerImg, mapZoom.get()), 0, 0);
                                //null images ignored
                                layerGContext.drawImage(typeImg, 0, 0);
                            });

                            return null;
                        }
                    }.call();
                }
                catch (final Exception except) {
                    Logger.logThrowable("Exception in redrawLayer(" + layer + ")", except);
                }
            }

            /**
             * Redraws all layers, including tile types if they are set to be visible.
             */
            private void redrawAllLayers() {
                for (int i = 0; i < tileLayers.length; ++i) {
                    redrawLayer(i);
                }
            }

            private void redrawEntities() {
                try {
                    new Task <Void>() {
                        @Override
                        protected Void call() throws Exception {
                            final PixelReader entitiesReader = entityImg.getPixelReader();
                            for (final PxPack.Entity entity : entities) {
                                //pull sprite
                            }
                            return null;
                        }
                    }.call();
                }
                catch (final Exception except) {
                    Logger.logThrowable("Exception in redrawEntities()", except);
                }

                entityCanvas.getGraphicsContext2D().setStroke(Color.BLACK);
                for (final PxPack.Entity e : entities) {
                    entityCanvas.getGraphicsContext2D().strokeRect(e.getX() * TILE_WIDTH * mapZoom.get(),
                                                                   e.getY() * TILE_WIDTH * mapZoom.get(),
                                                                   TILE_WIDTH * mapZoom.get(), TILE_HEIGHT * mapZoom.get());
                }
            }

            private void redrawGrid() {
                final GraphicsContext gridGraphicsContext = gridCanvas.getGraphicsContext2D();
                gridGraphicsContext.clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
                gridGraphicsContext.setStroke(Color.WHITE);
                gridGraphicsContext.setLineWidth(1.0);

                for (int y = 0; y < gridCanvas.getHeight(); y += (TILE_HEIGHT * mapZoom.get())) {
                    gridGraphicsContext.strokeLine(0, y, gridCanvas.getWidth() - 1, y);
                }
                for (int x = 0; x < gridCanvas.getWidth(); x += (TILE_WIDTH * mapZoom.get())) {
                    gridGraphicsContext.strokeLine(x, 0, x, gridCanvas.getHeight() - 1);
                }
            }

            /**
             * Fixes the sizes of the {@code Canvas}es
             */
            private void fixCanvasSizes() {
                int maxWidth = 0, maxHeight = 0;
                for (int i = 0; i < tileLayers.length; ++i) {
                    final int[][] tiles = tileLayers[i].getTiles();
                    if (null == tiles) {
                        mapCanvases[i].setWidth(0);
                        mapCanvases[i].setHeight(0);
                        continue;
                    }

                    final int width = tiles[0].length * TILE_WIDTH * mapZoom.get();
                    final int height = tiles.length * TILE_HEIGHT * mapZoom.get();

                    if (width > maxWidth) {
                        maxWidth = width;
                    }
                    if (height > maxHeight) {
                        maxHeight = height;
                    }

                    mapCanvases[i].setWidth(width);
                    mapCanvases[i].setHeight(height);
                }

                entityCanvas.setWidth(maxWidth);
                entityCanvas.setHeight(maxHeight);

                cursorCanvas.setWidth(maxWidth);
                cursorCanvas.setHeight(maxHeight);

                gridCanvas.setWidth(maxWidth);
                gridCanvas.setHeight(maxHeight);
            }

            private final class UndoableMapDrawEdit implements UndoableEdit {
                private final int layer;
                private final int x;
                private final int y;
                private final int[][] oldTiles;
                private final int[][] newTiles;

                UndoableMapDrawEdit(final int layer, final int x, final int y, final int[][] oldTiles,
                                    final int[][] newTiles) {
                    if (null == oldTiles) {
                        throw new NullArgumentException("UndoableMapDrawEdit", "oldTiles");
                    }
                    if (null == newTiles) {
                        throw new NullArgumentException("UndoableMapDrawEdit", "newTiles");
                    }

                    if (0 == oldTiles.length) {
                        throw new IllegalArgumentException("Attempt to initialize new UndoableMapDrawEdit " +
                                                           "with oldTiles with height of 0");
                    }
                    if (0 == oldTiles[0].length) {
                        throw new IllegalArgumentException("Attempt to initialize new UndoableMapDrawEdit " +
                                                           "with oldTiles with width of 0");
                    }

                    if (0 == newTiles.length) {
                        throw new IllegalArgumentException("Attempt to initialize new UndoableMapDrawEdit " +
                                                           "with newTiles with height of 0");
                    }
                    if (0 == newTiles[0].length) {
                        throw new IllegalArgumentException("Attempt to initialize new UndoableMapDrawEdit " +
                                                           "with newTiles with width of 0");
                    }

                    if (oldTiles.length != newTiles.length || oldTiles[0].length != newTiles[0].length) {
                        throw new IllegalArgumentException("Attempt to initialize new UndoableMapDrawEdit " +
                                                           "with oldTiles and newTiles with unequal dimensions");
                    }

                    this.layer = layer;
                    this.x = x;
                    this.y = y;

                    this.oldTiles = new int[oldTiles.length][oldTiles[0].length];
                    for (int i = 0; i < oldTiles.length; ++i) {
                        System.arraycopy(oldTiles[i], 0, this.oldTiles[i], 0, oldTiles[i].length);
                    }

                    this.newTiles = new int[newTiles.length][newTiles[0].length];
                    for (int i = 0; i < newTiles.length; ++i) {
                        System.arraycopy(newTiles[i], 0, this.newTiles[i], 0, newTiles[i].length);
                    }
                }

                @Override
                public void undo() {
                    for (int y = this.y; y < this.y + oldTiles.length; ++y) {
                        for (int x = this.x; x < this.x + oldTiles[y - this.y].length; ++x) {
                            tileLayers[layer].setTile(x, y, oldTiles[y - this.y][x - this.x]);
                            redrawTile(layer, x, y);
                        }
                    }
                }

                @Override
                public void redo() {
                    for (int y = this.y; y < this.y + newTiles.length; ++y) {
                        for (int x = this.x; x < this.x + newTiles[y - this.y].length; ++x) {
                            tileLayers[layer].setTile(x, y, newTiles[y - this.y][x - this.x]);
                            redrawTile(layer, x, y);
                        }
                    }
                }
            }

            private final class UndoableMapResizeEdit implements UndoableEdit {
                //TODO: Instead, store one array of largest size (either pre or post-resize) as well as old and new dimensions
                private final int layer;
                private final int[][] oldTiles;
                private final int[][] newTiles;

                UndoableMapResizeEdit(final int layer, final int[][] oldTiles, final int[][] newTiles) {
                    this.layer = layer;

                    if (null == oldTiles || 0 == oldTiles.length || 0 == oldTiles[0].length) {
                        this.oldTiles = null;
                    }
                    else {
                        this.oldTiles = new int[oldTiles.length][oldTiles[0].length];
                        for (int i = 0; i < oldTiles.length; ++i) {
                            System.arraycopy(oldTiles[i], 0, this.oldTiles[i], 0, oldTiles[i].length);
                        }
                    }

                    if (null == newTiles || 0 == newTiles.length || 0 == newTiles[0].length) {
                        this.newTiles = null;
                    }
                    else {
                        this.newTiles = new int[newTiles.length][newTiles[0].length];
                        for (int i = 0; i < newTiles.length; ++i) {
                            System.arraycopy(newTiles[i], 0, this.newTiles[i], 0, newTiles[i].length);
                        }
                    }
                }

                @Override
                public void undo() {
                    if (null == oldTiles) {
                        tileLayers[layer].resize(0, 0);
                    }
                    else {
                        tileLayers[layer].resize(oldTiles[0].length, oldTiles.length);
                        for (int y = 0; y < oldTiles.length; ++y) {
                            for (int x = 0; x < oldTiles[y].length; ++x) {
                                tileLayers[layer].setTile(x, y, oldTiles[y][x]);
                            }
                        }
                    }

                    redrawLayer(layer);
                    fixCanvasSizes();
                    redrawLayer(layer);
                }

                @Override
                public void redo() {
                    if (null == newTiles) {
                        tileLayers[layer].resize(0, 0);
                    }
                    else {
                        tileLayers[layer].resize(newTiles[0].length, newTiles.length);
                        for (int y = 0; y < newTiles.length; ++y) {
                            for (int x = 0; x < newTiles[y].length; ++x) {
                                tileLayers[layer].setTile(x, y, newTiles[y][x]);
                            }
                        }
                    }

                    redrawLayer(layer);
                    fixCanvasSizes();
                    redrawLayer(layer);
                }
            }
        }
    }

    private final class PropertyEditTab extends FXUtil.FileEditTab {
        private final GridPane mainGridPane;

        private final PxPack.Head head;

        private final MapEditTab parent;

        PropertyEditTab(final MapEditTab parent) {
            super(Messages.getString("MapEditTab.PropertyEditTab.TITLE"));
            this.parent = parent;

            head = map.getHead();

            mainGridPane = new GridPane();
        }

        @Override
        public void undo() {
            //does nothing
        }

        @Override
        public void redo() {
            //does nothing
        }

        @Override
        public void save() {
            //does nothing
        }

        //Made public so the parent MapEditTab can call it in save()
        @Override
        public void setChanged(final boolean changed) {
            super.setChanged(changed);
        }
    }

    public enum DrawMode implements SafeOrdinalEnum <DrawMode> {
        DRAW,
        RECT,
        COPY,
        FILL,
        REPLACE;

        public static final EnumMap <DrawMode, Integer> ordinalMap = DRAW.ordinalMap(DrawMode.class);
    }

    public enum LayerFlag implements SafeOrdinalEnum <LayerFlag> {
        FOREGROUND,
        MIDDLEGROUND,
        BACKGROUND;

        static final EnumMap <LayerFlag, Integer> ordinalMap = FOREGROUND.ordinalMap(LayerFlag.class);
    }

    public enum ViewFlag implements SafeOrdinalEnum <ViewFlag> {
        TILE_TYPES,
        GRID,
        ENTITY_BOXES,
        ENTITY_SPRITES,
        ENTITY_NAMES;

        static final EnumMap <ViewFlag, Integer> ordinalMap = TILE_TYPES.ordinalMap(ViewFlag.class);
    }

    private enum MapPaneMenuItem implements SafeOrdinalEnum <MapPaneMenuItem> {
        RESIZE,
        BG_COLOR;

        static final EnumMap <MapPaneMenuItem, Integer> ordinalMap = RESIZE.ordinalMap(MapPaneMenuItem.class);
    }
}