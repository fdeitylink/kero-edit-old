/*
 * TODO:
 * Try using resize() instead of setWidth() and setHeight()
 * Resizing map & tileset is slightly slow
 * Detect if tileset is not square
 * For map - draw tile types on separate layer just like with the tileset pane
 * Find workaround for NPE with large Canvas (19tunnel maps in KB); current workaround is minimal map zoom
 * - https://bugs.openjdk.java.net/browse/JDK-8089835
 * Throw error on tileset load for 0 dimension?
 * Make method for redrawing subset/region of map
 * Improve multiple selection in tileset
 * Put exception strings in messages.properties?
 * Handle images not existing...
 *  - Figure out what Image does if file doesn't exist
 */

package io.fdeitylink.keroedit.mapedit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;

import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
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

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.application.Platform;

import javafx.geometry.Rectangle2D;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.util.ArrayIndexEnum;

import io.fdeitylink.keroedit.util.MathUtil;

import io.fdeitylink.keroedit.util.FXUtil;

import io.fdeitylink.keroedit.KeroEdit;
import io.fdeitylink.keroedit.Config;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.keroedit.util.edit.FileEditTab;
import io.fdeitylink.keroedit.util.edit.UndoableEdit;

import io.fdeitylink.keroedit.gamedata.GameData;

import io.fdeitylink.keroedit.map.PxPack;
import io.fdeitylink.keroedit.image.PxAttrManager;
import io.fdeitylink.keroedit.image.ImageManager;

import io.fdeitylink.keroedit.script.ScriptEditTab;

import static io.fdeitylink.keroedit.image.ImageDimensions.TILE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimensions.TILE_HEIGHT;
import static io.fdeitylink.keroedit.image.ImageDimensions.TILESET_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimensions.TILESET_HEIGHT;

import static io.fdeitylink.keroedit.image.ImageDimensions.PXATTR_TILE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimensions.PXATTR_TILE_HEIGHT;
import static io.fdeitylink.keroedit.image.ImageDimensions.PXATTR_IMAGE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimensions.PXATTR_IMAGE_HEIGHT;

public final class MapEditTab extends FileEditTab {
    private static final SimpleIntegerProperty mapZoom = new SimpleIntegerProperty(Config.mapZoom);
    private static final SimpleIntegerProperty tilesetZoom = new SimpleIntegerProperty(Config.tilesetZoom);

    private static final SimpleObjectProperty <Color> tilesetBgColor = new SimpleObjectProperty <>(Config.tilesetBgColor);

    private static final SimpleIntegerProperty displayedLayers = new SimpleIntegerProperty(Config.displayedLayers);
    private static final SimpleIntegerProperty selectedLayer = new SimpleIntegerProperty(0);

    private static final SimpleObjectProperty <KeroEdit.DrawSettingsItems> drawMode =
            new SimpleObjectProperty <>(KeroEdit.DrawSettingsItems.DRAW);

    private static final SimpleBooleanProperty showTileTypes = new SimpleBooleanProperty(false);
    //TODO: Use this for all view settings (tile types, grid) instead of showTileTypes
    //private static final SimpleIntegerProperty viewSettings = new SimpleIntegerProperty(0);

    private static Image pxAttrImg;
    private static Image entityImg;

    private static final Stage tilesetStage; //TODO: Change to Dialog when adding (not setting) event handlers is allowed
    //(or rework how the stage works)

    static {
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
    private /*final*/ PxPack map;

    public MapEditTab(final String mapFileName) throws IOException, ParseException {
        initImages();

        final String fullMapPath = GameData.getResourceFolder().toAbsolutePath().toString() +
                                   File.separatorChar + "field" + File.separatorChar +
                                   mapFileName + ".pxpack";
        try {
            map = new PxPack(Paths.get(fullMapPath));
        }
        catch (final IOException | ParseException except) {
            final String title;
            final String message;

            if (except instanceof IOException) {
                title = Messages.getString("MapEditTab.OpenIOExcept.TITLE");
                message = except.getMessage();

                Logger.logThrowable(except); //TODO: Log in PxPack instead?
            }
            else {
                title = Messages.getString("MapEditTab.OpenParseExcept.TITLE");
                message = MessageFormat.format(Messages.getString("MapEditTab.OpenParseExcept.MESSAGE"),
                                               mapFileName, except.getMessage());
            }

            FXUtil.createAlert(Alert.AlertType.ERROR, title, null, message).showAndWait();

            throw except;
        }

        setText(mapFileName);
        setId(mapFileName);
        setTooltip(new Tooltip(fullMapPath + "\n" + map.getHead().getDescription())); //ensure updates to description reflected in tooltip

        //TODO: Context menu? close and rename

        tabPane = new TabPane(new TileEditTab(this),
                              new ScriptEditTab(Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                          File.separatorChar + "text" +
                                                          File.separatorChar + map.getName() + ".pxeve"), false),
                              new PropertyEditTab());
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

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

    public static void bindDisplayedLayers(final SimpleIntegerProperty property) {
        displayedLayers.bind(property);
    }

    public static void setSelectedLayer(final int layer) {
        if (layer < 0 || layer > 2) {
            throw new IllegalArgumentException("Attempt to set selected layer to value outside range 0 - 2 (layer: " + layer + ")");
        }
        selectedLayer.set(layer);
    }

    public static void setDrawMode(final KeroEdit.DrawSettingsItems mode) {
        drawMode.set(mode);
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

    private final class TileEditTab extends FileEditTab {
        private final PxPack.Head head; //ensure updates to head reflected in PropertyEditTab
        private final ArrayList <PxPack.Entity> entities;

        private final MapPane mapPane;
        private final TilesetPane tilesetPane;

        private final int[][][] selectedTiles;

        //private final MapEditTab parent;

        TileEditTab(final MapEditTab parent) {
            super(Messages.getString("MapEditTab.TileEditTab.TITLE"));
            //this.parent = parent;

            selectedTiles = new int[map.getTileLayers().length][1][1];

            head = map.getHead(); //TODO: Store same head as properties tab in order to automatically update properties
            entities = map.getEntities();

            //TODO: background on map pane
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
         *
         * @param sPane The {@code SplitPane} containing the {@code TilesetPane} to be swapped
         * between the main {@code Stage} and a secondary {@code Stage}
         * @param parent The {@code MapEditTab} that holds this {@code TileEditTab}
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
            private final Canvas selectedRectCanvas;
            private final ImageView selectedTilesImgView;

            private final Rectangle2D[] selectedRegions;

            private Image[] tilesets;
            private ArrayList <ReadOnlyObjectProperty <PxAttrManager.PxAttr>> pxAttrs; //put this up in TileEditTab?

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

                tileTypeCanvas.visibleProperty().bind(showTileTypes);
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
                                /*if (null == selectedTilesRect || null == head.getTilesetNames()[layer]) {
                                    return null;
                                }*/

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
                    redrawTileTypes.start();

                    drawSelectedTiles.start();

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
            }

            private void initEventHandlers() {
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
                            //grabs x & y and bounds them to be within tileset size
                            final int x = MathUtil.boundInt((int)event.getX(), 0, (int)(tilesetCanvas.getWidth() - 1)) /
                                          tilesetZoom.get() / TILE_WIDTH;
                            final int y = MathUtil.boundInt((int)event.getY(), 0, (int)(tilesetCanvas.getHeight() - 1)) /
                                          tilesetZoom.get() / TILE_HEIGHT;

                            if (x != prevX || y != prevY) {
                                prevX = x;
                                prevY = y;

                                if (0 == tilesets[selectedLayer.get()].getWidth() ||
                                    0 == tilesets[selectedLayer.get()].getHeight()) {
                                    selectedRegions[selectedLayer.get()] = new Rectangle2D(0, 0, 1, 1);
                                    selectedTiles[selectedLayer.get()] = new int[][]{{0}}; //TODO: make array empty?
                                    redrawSelectedRect();
                                    return;
                                }

                                final Rectangle2D selectedRect = selectedRegions[selectedLayer.get()];

                                final int dx = (int)(x - (selectedRect.getMaxX() - 1));
                                final int dy = (int)(y - (selectedRect.getMaxY() - 1));

                                //TODO: Make dragging into relative negatives work better
                                //should probably draw a graph or something and map movements out
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

            private void fixSize() {
                tilesetCanvas.setWidth(TILESET_WIDTH * tilesetZoom.get());
                tilesetCanvas.setHeight(TILESET_HEIGHT * tilesetZoom.get());
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
        }

        private class MapPane extends ScrollPane {
            private final SimpleObjectProperty <Color> bgColor;

            //TODO: Add listener for size for when a layer is resized?
            private final PxPack.TileLayer[] tileLayers;

            private final Canvas[] mapCanvases;
            private final Canvas entityCanvas;
            private final Canvas cursorCanvas;
            private final StackPane mapStackPane;

            MapPane() {
                tileLayers = map.getTileLayers();

                mapCanvases = new Canvas[tileLayers.length];
                for (int i = 0; i < mapCanvases.length; ++i) {
                    mapCanvases[i] = new Canvas();
                    mapCanvases[i].setVisible(LayerFlags.values()[i].flag ==
                                              (displayedLayers.get() & LayerFlags.values()[i].flag));
                }

                entityCanvas = new Canvas();

                cursorCanvas = new Canvas();
                cursorCanvas.getGraphicsContext2D().setStroke(Color.WHITE);
                cursorCanvas.getGraphicsContext2D().setLineWidth(2.0);

                fixCanvasSizes();

                mapStackPane = new StackPane();
                mapStackPane.setAlignment(Pos.TOP_LEFT);
                for (int i = mapCanvases.length - 1; i > -1; --i) {
                    mapStackPane.getChildren().add(mapCanvases[i]);
                }
                mapStackPane.getChildren().addAll(entityCanvas, cursorCanvas);

                bgColor = new SimpleObjectProperty <>(head.getBgColor());
                bgColor.addListener((observable, oldValue, newValue) -> FXUtil.setBackgroundColor(newValue, mapStackPane));
                FXUtil.setBackgroundColor(bgColor.get(), mapStackPane);

                initEventHandlers();

                setPannable(false);
                setContextMenu(initContextMenu());
                setContent(mapStackPane);

                //redrawEntities();
            }

            /**
             * Binds {@code EventHandler}s to all the {@code Pane}s and
             * {@code Canvas}es in this {@code ScrollPane}
             */
            private void initEventHandlers() {
                displayedLayers.addListener(((observable, oldValue, newValue) -> {
                    for (int i = 0; i < LayerFlags.values().length; ++i) {
                        mapCanvases[i].setVisible(LayerFlags.values()[i].flag ==
                                                  (newValue.intValue() & LayerFlags.values()[i].flag));
                    }
                }));

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
                            switch (drawMode.get()) {
                                case DRAW:
                                    //TODO: account for rects of seleced tiles
                                    final int[][] tilesRect = selectedTiles[selectedLayer.get()];
                                    cursorGContext.strokeRoundRect(x * TILE_WIDTH * mapZoom.get(),
                                                                   y * TILE_HEIGHT * mapZoom.get(),
                                                                   TILE_WIDTH * tilesRect[0].length * mapZoom.get(),
                                                                   TILE_HEIGHT * tilesRect.length * mapZoom.get(),
                                                                   10, 10);
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
                                        final int x = MathUtil.boundInt((int)event.getX(), 0,
                                                                        (int)(mapCanvases[layer].getWidth() - 1)) /
                                                      mapZoom.get() / TILE_WIDTH;
                                        final int y = MathUtil.boundInt((int)event.getY(), 0,
                                                                        (int)(mapCanvases[layer].getHeight() - 1)) /
                                                      mapZoom.get() / TILE_HEIGHT;

                                        if (y < tileLayers[layer].getTiles().length &&
                                            x < tileLayers[layer].getTiles()[y].length) {

                                            //caps lengths in the event x or y is close to map edge such that selected tiles go off edge
                                            final int hLen = Math.min(tiles[0].length - x, selectedTiles[layer][0].length);
                                            final int vLen = Math.min(tiles.length - y, selectedTiles[layer].length);

                                            final int[][] newTiles = new int[vLen][hLen];
                                            //newTiles potentially smaller in either dimension than selectedTiles, as per hLen & vLen
                                            for (int r = 0; r < newTiles.length; ++r) {
                                                System.arraycopy(selectedTiles[layer][r], 0, newTiles[r], 0, newTiles[r].length);
                                            }

                                            final int[][] oldTiles = new int[newTiles.length][newTiles[0].length];

                                            for (int r = y; r < y + newTiles.length && r < tiles.length; ++r) {
                                                System.arraycopy(tiles[r], x, oldTiles[r - y], 0, oldTiles[r - y].length);

                                                for (int c = x; c < x + newTiles[r - y].length && c < tiles[r].length; ++c) {
                                                    if (oldTiles[r - y][c - x] != newTiles[r - y][c - x]) {
                                                        tileLayers[layer].setTile(c, r, newTiles[r - y][c - x]);
                                                        redrawTile(layer, c, r);
                                                    }
                                                }
                                            }

                                            if (!Arrays.deepEquals(oldTiles, newTiles)) {
                                                //TODO: call setChanged() in parent
                                                setChanged(true);

                                                getRedoStack().clear();
                                                getUndoStack().addFirst(new UndoableMapDrawEdit(layer, x, y, oldTiles, newTiles));
                                            }
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

                menuItems[MapPaneMenuItems.arrIndexEnumMap.get(MapPaneMenuItems.RESIZE)].setOnAction(event -> {
                    final String layerName;
                    switch (selectedLayer.get()) {
                        case 0:
                            layerName = Messages.getString("LayerNames.FOREGROUND");
                            break;
                        case 1:
                            layerName = Messages.getString("LayerNames.MIDDLEGROUND");
                            break;
                        case 2:
                        default:
                            layerName = Messages.getString("LayerNames.BACKGROUND");
                    }

                    final String title = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.TITLE"),
                                                              layerName);

                    final int[][] layerData = mapPane.tileLayers[selectedLayer.get()].getTiles();
                    final int width = null == layerData ? 0 : layerData[0].length;
                    final int height = null == layerData ? 0 : layerData.length;

                    final String currentSizeStr = MessageFormat.format(Messages.getString("MapEditTab.TileEditTab.Resize.CURRENT_SIZE"),
                                                                       width, height);

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

                            tileLayers[selectedLayer.get()].resize(newWidth, newHeight);

                            fixCanvasSizes();
                            redrawLayer(selectedLayer.get());

                            setChanged(true);
                        }
                    });
                });

                menuItems[MapPaneMenuItems.arrIndexEnumMap.get(MapPaneMenuItems.BG_COLOR)].setOnAction(event -> {
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
                            final boolean drawTileType;

                            if (showTileTypes.get() && selectedLayer.get() == layer) {
                                attributes = tilesetPane.pxAttrs.get(selectedLayer.get()).get().getAttributes();
                                drawTileType = null != attributes;
                            }
                            else {
                                attributes = null;
                                drawTileType = false;
                            }

                            final int tilesetX = tileIndex % (TILESET_WIDTH / TILE_WIDTH);
                            final int tilesetY = tileIndex / (TILESET_HEIGHT / TILE_HEIGHT);

                            tileImg = FXUtil.scaleImage(new WritableImage(tilesetPane.tilesets[layer].getPixelReader(),
                                                                          tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT,
                                                                          TILE_WIDTH, TILE_HEIGHT),
                                                        mapZoom.get());
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
                            if (null == tilesetReader) { //this should handle empty/nonexistent images
                                return null;
                            }

                            final WritableImage tmpLayerImg = new WritableImage(layerData[0].length * TILE_WIDTH,
                                                                                layerData.length * TILE_HEIGHT);
                            final PixelWriter tmpLayerImgWriter = tmpLayerImg.getPixelWriter();

                            ////////////////////////////////////////////////////////////////////////////////////////////
                            final PixelReader pxAttrImgReader;
                            final WritableImage tmpTileTypeImg;
                            final PixelWriter tmpTileTypeImgWriter;
                            final int[][] attributes;
                            final byte[] attrTile;

                            final boolean drawTileTypes;

                            if (showTileTypes.get() && selectedLayer.get() == layer) {
                                pxAttrImgReader = pxAttrImg.getPixelReader();
                                tmpTileTypeImg = new WritableImage((int)tmpLayerImg.getWidth() * 2,
                                                                   (int)tmpLayerImg.getHeight() * 2);
                                tmpTileTypeImgWriter = tmpTileTypeImg.getPixelWriter();

                                attributes = tilesetPane.pxAttrs.get(selectedLayer.get()).get().getAttributes();
                                attrTile = new byte[PXATTR_TILE_WIDTH * PXATTR_TILE_HEIGHT * 4];

                                drawTileTypes = null != attributes;
                            }
                            else {
                                pxAttrImgReader = null;
                                tmpTileTypeImg = null;
                                tmpTileTypeImgWriter = null;
                                attributes = null;
                                attrTile = null;

                                drawTileTypes = false;
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

                            Platform.runLater(() -> {
                                final GraphicsContext layerGContext = mapCanvases[layer].getGraphicsContext2D();

                                layerGContext.clearRect(0, 0, mapCanvases[layer].getWidth(), mapCanvases[layer].getHeight());

                                layerGContext.drawImage(FXUtil.scaleImage(tmpLayerImg, mapZoom.get()), 0, 0);
                                //null images ignored
                                layerGContext.drawImage(FXUtil.scaleImage(tmpTileTypeImg, mapZoom.get() / 2), 0, 0);
                            });

                            return null;
                        }
                    }.call();
                }
                catch (final Exception except) {
                    Logger.logThrowable("Exception in redrawLayer(" + layer + ")", except);
                }
            }

            private void redrawEntities() {
                try {
                    new Task <Void>() {
                        @Override
                        protected Void call() {
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

                entityCanvas.setWidth(maxWidth);
                entityCanvas.setHeight(maxHeight);
            }

            private class UndoableMapDrawEdit implements UndoableEdit {
                private final int layer;
                private final int x;
                private final int y;
                private final int[][] oldTiles;
                private final int[][] newTiles;

                UndoableMapDrawEdit(final int layer, final int x, final int y, final int[][] oldTiles, final int[][] newTiles) {
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

            //TODO: UndoableMapResizeEdit
        }
    }

    //TODO: Make this extend FileEditTab?
    private final class PropertyEditTab extends Tab {
        private final GridPane mainGridPane;

        private final PxPack.Head head;

        PropertyEditTab() {
            super(Messages.getString("MapEditTab.PropertyEditTab.TITLE"));

            head = map.getHead();

            mainGridPane = new GridPane();
        }
    }

    public enum LayerFlags {
        FOREGROUND(0b1),
        MIDDLEGROUND(0b10),
        BACKGROUND(0b100);

        public final int flag;

        LayerFlags(int flag) {
            this.flag = flag;
        }
    }

    private enum MapPaneMenuItems implements ArrayIndexEnum <MapPaneMenuItems> {
        RESIZE,
        BG_COLOR;

        static final EnumMap <MapPaneMenuItems, Integer> arrIndexEnumMap = RESIZE.enumMap(MapPaneMenuItems.class);
    }
}