/*
 * TODO:
 * Try using resize() instead of setWidth() and setHeight()
 * Resizing map & tileset is slightly slow
 * For map - draw tile types on separate layer just like with the tileset pane
 * Find workaround for NPE with large Canvas (19tunnel maps in KB); current workaround is minimal map zoom
 * - https://bugs.openjdk.java.net/browse/JDK-8089835
 * Show error on tileset load for 0 dimension? (only if first tileset?)
 * Make method for redrawing subset/region of map
 * Put map loading into Task or Service
 * Make initResources() a Service?
 * Use image.getWidth()/Height() instead of TILESET_WIDTH/HEIGHT constants?
 * Have resize remove entities (if resize makes map smaller in a dimension)
 * Put PxAttr attribute setting into Task? (since it saves on every change)
 * Offset tile and grid drawing by width/2 (check how the game displays tiles - by middle or top-left corner)
 * Skip loading PxAttr if tileset is not present?
 * Show error if tileset file doesn't exist? (do this in PxPack?)
 * Error check layer, coordinate arguments for UndoableEdit and PxAttrPopup constructors
 * Why doesn't selected rect draw on tileset until after user makes first click or changes layer?
 * Glob draw edits together based on single mouse drag/click
 * Make redrawing entities a Service
 * Method for redrawing a single entity?
 * Pixel renders the game at 2x (so 200% zoom looks like it would in the game assuming the scale there is set to 1x)
 * Have redrawTile() and redrawTileLayer() take a Layer object rather than an int layer
 */

package io.fdeitylink.keroedit.mapedit;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.util.ArrayList;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;
import java.text.ParseException;

import java.text.MessageFormat;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;

import javafx.stage.Popup;

import javafx.scene.layout.Pane;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Insets;

import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

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

import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SingleSelectionModel;

import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;

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

import javafx.application.Platform;
import javafx.concurrent.Service;

import javafx.geometry.Rectangle2D;
import javafx.geometry.Point2D;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;

import io.fdeitylink.util.NullArgumentException;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.util.UtilsKt;

import io.fdeitylink.util.Logger;

import io.fdeitylink.util.SafeEnum;

import io.fdeitylink.util.fx.FXUtil;
import io.fdeitylink.util.fx.FileEditTab;

import io.fdeitylink.util.fx.UndoableEdit;

import io.fdeitylink.keroedit.KeroEdit;

import io.fdeitylink.keroedit.resource.ResourceManager;

import io.fdeitylink.keroedit.Config;

import io.fdeitylink.keroedit.gamedata.GameData;

import io.fdeitylink.keroedit.map.PxPack;

import io.fdeitylink.keroedit.image.PxAttrManager;
import io.fdeitylink.keroedit.image.PxAttr;
import io.fdeitylink.keroedit.image.ImageManager;

import io.fdeitylink.keroedit.script.ScriptEditTab;

import static io.fdeitylink.keroedit.image.ImageDimension.ENTITIES_PER_ROW;
import static io.fdeitylink.keroedit.image.ImageDimension.ENTITY_HEIGHT;
import static io.fdeitylink.keroedit.image.ImageDimension.ENTITY_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.TILE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.TILE_HEIGHT;

import static io.fdeitylink.keroedit.image.ImageDimension.TILES_PER_ROW;

import static io.fdeitylink.keroedit.image.ImageDimension.TILESET_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.TILESET_HEIGHT;

import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_TILE_WIDTH;
import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_TILE_HEIGHT;

import static io.fdeitylink.keroedit.image.ImageDimension.PXATTR_TILES_PER_ROW;

public final class MapEditTab extends FileEditTab {
    //private static int numInstances; //used to tell if this is the last MapEditTab open

    private static final SimpleDoubleProperty mapZoom;
    private static final SimpleDoubleProperty tilesetZoom;

    private static final SimpleObjectProperty <Color> tilesetBgColor;

    private static final SimpleObjectProperty <EnumSet <Layer>> displayedLayers;
    private static final SimpleObjectProperty <Layer> selectedLayer;

    private static final SimpleObjectProperty <DrawMode> drawMode;

    private static final SimpleObjectProperty <EnumSet <ViewOption>> viewSettings;

    private static final SimpleObjectProperty <EditMode> editMode;

    private static Image pxAttrImage;
    private static Image entityImage;
    private static String[] entityNames;

    //TODO: Change to Dialog when adding (not setting) event handlers is allowed (or rework how the stage works)
    private static final Stage tilesetStage;
    private static final Pane EMPTY_PANE;

    static {
        /* *********************************************** Properties *********************************************** */
        mapZoom = new SimpleDoubleProperty(Config.INSTANCE.getMapZoom());
        tilesetZoom = new SimpleDoubleProperty(Config.INSTANCE.getTilesetZoom());
        tilesetBgColor = new SimpleObjectProperty <>(Config.tilesetBgColor);

        displayedLayers = new SimpleObjectProperty <>(Config.displayedLayers);
        selectedLayer = new SimpleObjectProperty <>(Config.INSTANCE.getSelectedLayer());

        drawMode = new SimpleObjectProperty <>(Config.drawMode);

        viewSettings = new SimpleObjectProperty <>(Config.viewSettings);

        editMode = new SimpleObjectProperty <>(Config.editMode);

        /* ********************************************** Tileset Stage ********************************************* */
        EMPTY_PANE = new Pane(); //null not accepted as scene root, so this is used when tileset stage is not shown

        tilesetStage = new Stage();
        tilesetStage.setAlwaysOnTop(true); //TODO: minimize or hide when KeroEdit program not focused
        tilesetStage.setTitle(Messages.INSTANCE.getString("MapEditTab.TileEditTab.TILESET_WINDOW_TITLE"));
        tilesetStage.setScene(new Scene(EMPTY_PANE));

        tilesetStage.setOnCloseRequest(event -> {
            //remove tileset from stage
            tilesetStage.getScene().setRoot(EMPTY_PANE);
            Config.INSTANCE.setTilesetStageShowing(false);
        });

        tilesetStage.setOnShowing(event -> Config.INSTANCE.setTilesetStageShowing(true));

        if (Config.INSTANCE.getTilesetStageShowing()) {
            tilesetStage.show();
        }
    }

    private final TabPane tabPane;

    private final TileEditTab tileEditTab;
    private final ScriptEditTab scriptEditTab;
    private final PropertyEditTab propertyEditTab;

    private final Tooltip tooltip;

    private final PxPack map;

    public MapEditTab(Path inPath) throws IOException, ParseException {
        //TODO: Check if inPath is actually within the mod folder and throw except if not?
        super(inPath = inPath.toAbsolutePath());

        if (!GameData.INSTANCE.isInitialized()) {
            throw new IllegalStateException("Attempt to create MapEditTab when GameData has not been properly initialized yet");
        }

        final String fname = UtilsKt.baseFilename(inPath, GameData.mapExtension);

        try {
            map = new PxPack(inPath);
        }
        catch (final IOException | ParseException except) {
            final String title;
            final String message;

            if (except instanceof IOException) {
                title = Messages.INSTANCE.getString("MapEditTab.OpenIOExcept.TITLE");
                message = MessageFormat.format(Messages.INSTANCE.getString("MapEditTab.OpenIOExcept.MESSAGE"),
                                               fname, except.getMessage());

                Logger.INSTANCE.logThrowable("IOException in map parsing", except);
            }
            else {
                title = Messages.INSTANCE.getString("MapEditTab.OpenParseExcept.TITLE");
                message = MessageFormat.format(Messages.INSTANCE.getString("MapEditTab.OpenParseExcept.MESSAGE"),
                                               fname, except.getMessage());
            }

            FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR, title, null, message).showAndWait();

            throw except;
        }

        initResources();

        setText(fname);

        //TODO: Ensure updates to description in PropertyEditTab are reflected in tooltip
        tooltip = new Tooltip(inPath.toString() + '\n' +
                              Messages.INSTANCE.getString("MapEditTab.TOOLTIP_DESCRIPTION_LABEL") +
                              map.getHead().getDescription());
        setTooltip(tooltip);

        tileEditTab = new TileEditTab();

        //TODO: Don't fail/escalate exception if ScriptEditTab() throws IOException?
        scriptEditTab = new ScriptEditTab(Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                                    File.separatorChar + GameData.scriptFolder +
                                                    File.separatorChar + fname + GameData.scriptExtension), this);
        propertyEditTab = new PropertyEditTab();

        tabPane = new TabPane(tileEditTab, scriptEditTab, propertyEditTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        //TODO: Context menu? rename...

        setContent(tabPane);
        //numInstances++;
    }

    public static void setMapZoom(final double zoom) {
        if (0 >= zoom) {
            throw new IllegalArgumentException("Attempt to set map zoom level to be <= 0 (zoom: " + zoom + ')');
        }
        mapZoom.set(zoom);
    }

    public static void setTilesetZoom(final double zoom) {
        if (0 >= zoom) {
            throw new IllegalArgumentException("Attempt to set tileset zoom level to be <= 0 (zoom: " + zoom + ')');
        }
        tilesetZoom.set(zoom);
    }

    public static void setTilesetBgColor(final Color color) {
        tilesetBgColor.set(color);
    }

    public static void setDisplayedLayers(final EnumSet <Layer> flags) {
        //TODO: Observable value that fires change events when Enum is added to/removed from Set?
        //(similar to the thing for PxAttrs)
        displayedLayers.set(EnumSet.copyOf(flags));
    }

    public static void setSelectedLayer(final Layer layer) {
        selectedLayer.set(layer);
    }

    public static void setDrawMode(final DrawMode mode) {
        drawMode.set(mode);
    }

    public static void setViewSettings(final EnumSet <ViewOption> flags) {
        viewSettings.set(EnumSet.copyOf(flags));
    }

    public static void setEditMode(final EditMode mode) {
        editMode.set(mode);
    }

    public Path getScriptPath() {
        return scriptEditTab.getPath();
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
        try {
            map.save();
            tileEditTab.markUnchanged();
            propertyEditTab.markUnchanged();

            /*
             * If an IOException is thrown in ScriptEditTab's save() method,
             * it does not rethrow/escalate the exception. Instead it tells
             * the user with a dialog, and does not call setChanged(false)
             * (and thereby the '*' will remain on the tab's label).
             */
            scriptEditTab.save();

            if (!scriptEditTab.isChanged()) {
                markUnchanged();
            }
        }
        catch (final IOException except) {
            FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR, Messages.INSTANCE.getString("MapEditTab.Save.IOExcept.TITLE"), null,
                                        MessageFormat.format(Messages.INSTANCE.getString("MapEditTab.Save.IOExcept.MESSAGE"),
                                                             map.getName(), except.getMessage())).showAndWait();
        }
    }

    //Made public so the child ScriptEditTab can call it
    @Override
    public void markChanged() {
        super.markChanged();
    }

    @Override
    protected void markUnchanged() {
        super.markUnchanged();
        tileEditTab.markUnchanged();
        scriptEditTab.markUnchanged();
        propertyEditTab.markUnchanged();
    }

    /**
     * Wipes the stored PxAttr attribute and entity images from memory
     */
    public static void wipeResources() {
        pxAttrImage = null;
        entityImage = null;
        entityNames = null;
    }

    /**
     * Initializes the {@code pxAttrImage} and {@code entityImage} variables
     */
    private void initResources() {
        //TODO: Should ImageManager handle pxAttrImage & entityImage?
        if (null == pxAttrImage) {
            final Path attrPath = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                            File.separatorChar + "assist" + File.separatorChar +
                                            "attribute.png");
            if (Files.exists(attrPath)) {
                pxAttrImage = new Image(attrPath.toUri().toString(), false);
            }
            else {
                pxAttrImage = ResourceManager.INSTANCE.getImage("assist/attribute.png");
            }
        }
        if (null == entityImage) {
            final Path entityPath = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                              File.separatorChar + "assist" + File.separatorChar +
                                              "unittype.png");
            if (Files.exists(entityPath)) {
                entityImage = new Image(entityPath.toUri().toString(), false);
            }
            else {
                entityImage = ResourceManager.INSTANCE.getImage("assist/unittype.png");
            }
        }
        if (null == entityNames) {
            Path namesPath = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                       File.separatorChar + "assist" + File.separatorChar +
                                       "unittype.json");
            if (!Files.exists(namesPath)) {
                namesPath = ResourceManager.INSTANCE.getPath("assist/unittype.json");
            }

            /*
             * TODO:
             * Handle namesPath being null somehow
             * Catch ParseException and handle invalid input
             * Cap the number of entities read at the number of entities that exist
             */
            //TODO: Handle namesPath being null somehow
            //TODO: Catch ParseException, handle invalid input
            try (BufferedReader namesReader = Files.newBufferedReader(namesPath, Charset.forName("UTF-8"))) {
                final JsonArray names = Json.parse(namesReader).asObject().get("entities").asArray();
                entityNames = new String[names.size()];
                for (int i = 0; i < names.size(); ++i) {
                    entityNames[i] = names.get(i).asString();
                }

            }
            catch (final IOException except) {

            }
        }
    }

    private final class TileEditTab extends FileEditTab {
        private final PxPack.Head head;
        private final ArrayList <PxPack.Entity> entities;

        private final TilesetPane tilesetPane;
        private final EntityPane entityPane;
        private final MapPane mapPane;

        private final int[][][] selectedTiles;

        TileEditTab() {
            super(MapEditTab.this.getPath(), Messages.INSTANCE.getString("MapEditTab.TileEditTab.TITLE"));

            selectedTiles = new int[PxPack.NUM_LAYERS][1][1];

            head = map.getHead();
            entities = map.getEntities();

            tilesetPane = new TilesetPane();
            entityPane = new EntityPane();
            mapPane = new MapPane();

            //TODO: background on map pane (purple checkerboard?)
            final SplitPane sPane = new SplitPane();

            if (EditMode.TILE == editMode.get()) {
                sPane.getItems().add(tilesetPane);
                sPane.setOrientation(Orientation.VERTICAL);
                sPane.setDividerPositions(0.1);
            }
            else { //EditMode.ENTITY
                sPane.getItems().add(entityPane);
                sPane.setOrientation(Orientation.HORIZONTAL);
                sPane.setDividerPositions(0.2);
                //tilesetStage.close();
                //TODO: The tileset stage needs to be closed/hidden while in entity mode
            }

            editMode.addListener((observable, oldValue, newValue) -> {
                if (EditMode.TILE == newValue) {
                    sPane.getItems().set(0, tilesetPane);
                    sPane.setOrientation(Orientation.VERTICAL);
                    sPane.setDividerPositions(0.1);
                }
                else { //EditMode.ENTITY
                    sPane.getItems().set(0, entityPane);
                    sPane.setOrientation(Orientation.HORIZONTAL);
                    sPane.setDividerPositions(0.2);
                    //tilesetStage.close();
                }

                sPane.setDividerPositions(0.1);
            });

            sPane.getItems().add(mapPane);

            initTilesetStage(sPane);

            setContent(sPane);
        }

        @Override
        public void save() {
            //does nothing
        }

        @Override
        protected void markChanged() {
            super.markChanged();
            MapEditTab.this.markChanged();
        }

        //Made public so the parent MapEditTab can call it
        @Override
        public void markUnchanged() {
            super.markUnchanged();
        }

        /**
         * Adds all the {@code EventHandler}s and other things
         * for setting up the tileset {@code Stage}
         *
         * @param sPane The {@code SplitPane} containing the {@code TilesetPane} to be swapped
         *              between the main {@code Stage} and a secondary {@code Stage}
         */
        private void initTilesetStage(final SplitPane sPane) {
            //TODO: Hide tilesetStage when editing entities, show when editing tiles

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
                if (EditMode.TILE == editMode.get()) {
                    sPane.getItems().set(0, tilesetPane);
                    sPane.setDividerPositions(0.1);
                }
            };
            tilesetStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, afterHiddenEvent);

            //shows or minimizes tilesetStage depending on if TileEditTab is selected or Properties/Script Tabs are selected
            setOnSelectionChanged(event -> tilesetStage.setIconified(!isSelected()));

            //undock tileset
            tilesetPane.setOnMouseClicked(event -> {
                if (MouseButton.PRIMARY == event.getButton() && 2 == event.getClickCount() &&
                    !tilesetStage.isShowing()) {
                    tilesetStage.show(); //also runs code of WINDOW_SHOWING EventHandler
                }
            });

            //change shown tilesetPane when selected MapEditTab is changed

            MapEditTab.this.setOnSelectionChanged(event -> {
                if (MapEditTab.this.isSelected()) {
                    if (tilesetStage.isShowing() && tilesetStage.getScene().getRoot() != tilesetPane) {
                        tilesetStage.getScene().setRoot(tilesetPane);
                        tilesetStage.setIconified(false);
                    }
                }
                //TODO: Minimize & clear tilesetStage when focused on non-MapEditTab tab
                //Doesn't seem to ever be triggered (maybe because it's set to be always on top?)
                /*else if (!(parent.getTabPane().getSelectionModel().getSelectedItem() instanceof MapEditTab)) {
                    tilesetStage.getScene().setRoot(EMPTY_PANE);
                    tilesetStage.setIconified(true);
                }*/
            });

            //if the tilesetStage is being shown on init, change shown tilesetPane to this one
            if (tilesetStage.showingProperty().get()) {
                beforeShowingEvent.handle(new WindowEvent(tilesetStage, WindowEvent.WINDOW_SHOWING));
            }
        }

        private final class TilesetPane extends SplitPane {
            private final Canvas tilesetCanvas;
            private final Canvas tileTypeCanvas;
            private final Canvas selectedRectCanvas;
            private final ImageView selectedTilesImgView;

            private final Point2D[] dragStartPoints;
            private final Rectangle2D[] selectedRects;

            private Image[] tilesets;

            //TODO: List of fixed size
            //private final ArrayList <ReadOnlyObjectProperty <PxAttr>> pxAttrs; //generic arrays not allowed
            private PxAttr[] pxAttrs;

            //TODO: Can these be made static?
            private Service <Void> redrawTileTypes;
            private Service <Void> drawSelectedTiles;
            private Service <Void> loadTilesets;
            private Service <Void> loadPxAttrs;

            TilesetPane() {
                initServices();
                loadTilesets.start();
                loadPxAttrs.start();

                tilesetCanvas = new Canvas();

                /* ******************************************** Canvases ******************************************** */
                tileTypeCanvas = new Canvas();
                tileTypeCanvas.widthProperty().bind(tilesetCanvas.widthProperty());
                tileTypeCanvas.heightProperty().bind(tilesetCanvas.heightProperty());

                tileTypeCanvas.setVisible(viewSettings.get().contains(ViewOption.TILE_TYPES));
                tileTypeCanvas.setOnMouseClicked(tilesetCanvas::fireEvent);

                selectedRectCanvas = new Canvas();
                selectedRectCanvas.widthProperty().bind(tilesetCanvas.widthProperty());
                selectedRectCanvas.heightProperty().bind(tilesetCanvas.heightProperty());

                /* ******************************************** Selection ******************************************* */
                final GraphicsContext selectedRectGContext = selectedRectCanvas.getGraphicsContext2D();
                selectedRectGContext.setStroke(Color.WHITE);
                selectedRectGContext.setLineWidth(2.0);

                dragStartPoints = new Point2D[PxPack.NUM_LAYERS];
                selectedRects = new Rectangle2D[PxPack.NUM_LAYERS];
                for (int i = 0; i < dragStartPoints.length; ++i) {
                    selectedRects[i] = new Rectangle2D(0, 0, 1, 1);
                    dragStartPoints[i] = Point2D.ZERO;
                }
                redrawSelectedRect();

                final ScrollPane tilesetScrollPane = new ScrollPane(new StackPane(tilesetCanvas, tileTypeCanvas,
                                                                                  selectedRectCanvas));
                tilesetScrollPane.setPannable(false);

                selectedTilesImgView = new ImageView();

                initEventHandlers();

                /*
                 * The ImageView is inside a Pane because otherwise it's pushed all the way to the left
                 * and the divider will only be moved by selecting more tiles, not by user's control.
                 */
                getItems().addAll(tilesetScrollPane, new Pane(selectedTilesImgView));
            }

            private void initServices() {
                redrawTileTypes = FXUtil.INSTANCE.service(() -> {
                    final GraphicsContext gContext = tileTypeCanvas.getGraphicsContext2D();

                    Platform.runLater(() -> gContext.clearRect(0, 0, tileTypeCanvas.getWidth(), tileTypeCanvas.getHeight()));

                    final PxAttr pxAttr = pxAttrs[selectedLayer.get().ordinal()];
                    if (null != pxAttr) {
                        final int[][] attributes = pxAttr.getAttributes();
                        final PixelReader pxAttrImgReader = pxAttrImage.getPixelReader();

                        if (null != attributes && null != pxAttrImgReader) {
                            final WritableImage tileTypeImg =
                                    new WritableImage((int)tileTypeCanvas.getWidth() * 2
                                                                            /*(PXATTR_IMAGE_WIDTH / TILESET_WIDTH)*/,
                                                      (int)tileTypeCanvas.getHeight() * 2
                                                                            /*(PXATTR_IMAGE_HEIGHT / TILESET_HEIGHT)*/);
                            final PixelWriter tileTypeImgWriter = tileTypeImg.getPixelWriter();

                            final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                            final byte[] attrTile = new byte[PXATTR_TILE_WIDTH * PXATTR_TILE_HEIGHT * 4];
                            for (int y = 0; y < attributes.length; ++y) {
                                for (int x = 0; x < attributes[y].length; ++x) {
                                    final int attributesX = attributes[y][x] % PXATTR_TILES_PER_ROW;
                                    final int attributesY = attributes[y][x] / PXATTR_TILES_PER_ROW;

                                    pxAttrImgReader.getPixels(attributesX * PXATTR_TILE_WIDTH,
                                                              attributesY * PXATTR_TILE_HEIGHT,
                                                              PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT,
                                                              pxFormat, attrTile, 0, PXATTR_TILE_WIDTH * 4);

                                    tileTypeImgWriter.setPixels(x * PXATTR_TILE_WIDTH, y * PXATTR_TILE_HEIGHT,
                                                                PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT,
                                                                pxFormat, attrTile, 0, PXATTR_TILE_WIDTH * 4);
                                }
                            }

                            Platform.runLater(() -> gContext
                                    .drawImage(FXUtil.INSTANCE.scale(tileTypeImg, tilesetZoom.get() / 2), 0, 0));
                        }
                    }

                    return null;
                });

                drawSelectedTiles = FXUtil.INSTANCE.service(() -> {
                    final int layer = selectedLayer.get().ordinal();

                    final int[][] selectedTilesRect = selectedTiles[layer];

                    final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                    final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();

                    final WritableImage tilesImg = new WritableImage(selectedTilesRect[0].length * TILE_WIDTH,
                                                                     selectedTilesRect.length * TILE_HEIGHT);
                    final PixelWriter tilesImgWriter = tilesImg.getPixelWriter();

                    final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];

                    for (int y = 0; y < selectedTilesRect.length; ++y) {
                        for (int x = 0; x < selectedTilesRect[y].length; ++x) {
                            final int tilesetX = selectedTilesRect[y][x] % TILES_PER_ROW;
                            final int tilesetY = selectedTilesRect[y][x] / TILES_PER_ROW;

                            tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT,
                                                    TILE_WIDTH, TILE_HEIGHT,
                                                    pxFormat, tile, 0, TILE_WIDTH * 4);

                            tilesImgWriter.setPixels(x * TILE_WIDTH, y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT,
                                                     pxFormat, tile, 0, TILE_WIDTH * 4);
                        }
                    }

                    //TODO: Change selectedTiles view to a Canvas?
                    Platform.runLater(() -> selectedTilesImgView.setImage(FXUtil.INSTANCE.scale(tilesImg, tilesetZoom.get())));
                    return null;
                });

                loadTilesets = FXUtil.INSTANCE.service(() -> {
                    tilesets = new Image[PxPack.NUM_LAYERS];

                    final String[] tilesetNames = head.getTilesetNames();
                    for (int i = 0; i < tilesets.length; ++i) {
                        tilesets[i] = ImageManager.getImage(tilesetNames[i], true);
                    }
                    return null;
                });
                loadTilesets.setOnSucceeded(event -> {
                    fixSize();
                    setDividerPositions(0.5);

                    redrawTileset();
                    redrawTileTypes.restart(); //TODO: Move into if block below?

                    drawSelectedTiles.restart();

                    //TODO: Doesn't this mean all the layers are redrawn twice?
                    //when both of the Services are complete, redraw layers
                    if (!loadPxAttrs.isRunning()) {
                        mapPane.redrawAllTileLayers();
                    }
                });

                loadPxAttrs = FXUtil.INSTANCE.service(() -> {
                    pxAttrs = new PxAttr[PxPack.NUM_LAYERS];

                    final String[] tilesetNames = head.getTilesetNames();
                    for (int i = 0; i < tilesetNames.length; ++i) {
                        try {
                            final ReadOnlyObjectProperty <PxAttr> pxAttrProp = PxAttrManager.getPxAttr(tilesetNames[i]);
                            pxAttrProp.addListener(observable -> {
                                redrawTileTypes.restart();
                                mapPane.redrawTileLayer(selectedLayer.get().ordinal());
                            });
                            pxAttrs[i] = pxAttrProp.get();
                        }
                        catch (final IOException | ParseException except) {
                            final String title = except instanceof IOException ?
                                                 Messages.INSTANCE.getString("MapEditTab.TileEditTab.PxAttrLoadIOExcept.TITLE") :
                                                 Messages.INSTANCE.getString("MapEditTab.TileEditTab.PxAttrLoadParseExcept.TITLE");
                            //TODO: Option to create one? (will have to be all blank...)
                            Platform.runLater(() -> FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR, title, null,
                                                                                except.getMessage()).showAndWait());
                        }
                    }

                    return null;
                });
                loadPxAttrs.setOnSucceeded(event -> {
                    //when both of the Services are complete, redraw layers
                    if (!loadTilesets.isRunning()) {
                        mapPane.redrawAllTileLayers();
                    }
                });
            }

            private void initEventHandlers() {
                viewSettings.addListener((observable, oldValue, newValue) ->
                                                 tileTypeCanvas.setVisible(newValue.contains(ViewOption.TILE_TYPES)));

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

                tilesetCanvas.setOnMousePressed(new EventHandler <MouseEvent>() {
                    private PxAttrPopup pxAttrPopup;

                    @Override
                    public void handle(final MouseEvent event) {
                        if (MouseButton.PRIMARY == event.getButton()) {
                            if (null != pxAttrPopup) {
                                pxAttrPopup.hide();
                                pxAttrPopup = null;
                            }

                            final int layer = selectedLayer.get().ordinal();
                            if (0 == tilesets[layer].getWidth() ||
                                0 == tilesets[layer].getHeight()) {
                                selectedTiles[layer] = new int[][]{{0}}; //TODO: make array empty?
                                dragStartPoints[layer] = Point2D.ZERO;

                                //TODO: private static final Rectangle2D for this?
                                selectedRects[layer] = new Rectangle2D(0, 0, 1, 1);
                                redrawSelectedRect();
                                return;
                            }

                            final int x = (int)(event.getX() / tilesetZoom.get() / TILE_WIDTH);
                            final int y = (int)(event.getY() / tilesetZoom.get() / TILE_HEIGHT);

                            selectedTiles[layer] = new int[][]{{(y * TILES_PER_ROW) + x}};

                            dragStartPoints[layer] = new Point2D(x, y);
                            selectedRects[layer] = new Rectangle2D(x, y, 1, 1);

                            drawSelectedTiles.restart();
                            redrawSelectedRect();
                        }
                        else if (MouseButton.SECONDARY == event.getButton()) {
                            if (null != pxAttrPopup) {
                                pxAttrPopup.hide();
                            }

                            final int tilesetX = (int)(event.getX() / tilesetZoom.get() / TILE_WIDTH);
                            final int tilesetY = (int)(event.getY() / tilesetZoom.get() / TILE_HEIGHT);
                            pxAttrPopup = new PxAttrPopup(selectedLayer.get().ordinal(), tilesetX, tilesetY);

                            pxAttrPopup.show(tilesetPane, event.getScreenX(), event.getScreenY());
                        }
                    }
                });

                tilesetCanvas.setOnMouseDragged(new EventHandler <MouseEvent>() {
                    private int prevX;
                    private int prevY;

                    @Override
                    public void handle(final MouseEvent event) {
                        if (MouseButton.PRIMARY == event.getButton()) {
                            final int layer = selectedLayer.get().ordinal();

                            if (0 == tilesets[layer].getWidth() ||
                                0 == tilesets[layer].getHeight()) {
                                return;
                            }

                            //grabs x & y and bounds them to be within tileset
                            final int x = (int)(UtilsKt.bound((int)event.getX(), 0, (int)tilesetCanvas.getWidth() - 1) /
                                                tilesetZoom.get() / TILE_WIDTH);
                            final int y = (int)(UtilsKt.bound((int)event.getY(), 0, (int)tilesetCanvas.getHeight() - 1) /
                                                tilesetZoom.get() / TILE_HEIGHT);

                            if (x != prevX || y != prevY) {
                                prevX = x;
                                prevY = y;

                                final Point2D dragStartPoint = dragStartPoints[layer];
                                final Rectangle2D prevSelRect = selectedRects[layer];

                                /*
                                 * TODO:
                                 * Fix bug where clicking on a spot, then dragging left/up,
                                 * then back onto the clicked spot retains the width/height.
                                 * The problem is with how dx and dy are calculated.
                                 */

                                final int dx = x - ((int)prevSelRect.getMaxX() - 1);
                                final int dy = y - ((int)prevSelRect.getMaxY() - 1);

                                final int minX, minY, width, height;
                                if (x < (int)dragStartPoint.getX()) {
                                    minX = x;
                                    width = (int)prevSelRect.getMaxX() - x;
                                }
                                else {
                                    minX = (int)dragStartPoint.getX();
                                    width = (int)prevSelRect.getWidth() + dx;
                                }

                                if (y < (int)dragStartPoint.getY()) {
                                    minY = y;
                                    height = (int)prevSelRect.getMaxY() - y;
                                }
                                else {
                                    minY = (int)dragStartPoint.getY();
                                    height = (int)prevSelRect.getHeight() + dy;
                                }

                                selectedRects[layer] = new Rectangle2D(minX, minY, width, height);

                                final int[][] tiles = new int[height][width];
                                //copies selected tiles into tiles 2D arr (created ^)
                                for (int tilesY = minY; tilesY < minY + height; ++tilesY) {
                                    for (int tilesX = minX; tilesX < minX + width; ++tilesX) {
                                        tiles[tilesY - minY][tilesX - minX] = (tilesY * TILES_PER_ROW) + tilesX;
                                    }
                                }
                                selectedTiles[layer] = tiles; //applies selection changes
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

                tilesetGContext.drawImage(FXUtil.INSTANCE.scale(tilesets[selectedLayer.get().ordinal()],
                                                                tilesetZoom.get()), 0, 0);

                //Since right now I only load the necessary part of the tileset, a simpler drawImage() method can be called (^)
                /*tilesetGContext.drawImage(FXUtil.INSTANCE.scale(tilesets[selectedLayer.get()], zoom.get()),
                                            0, 0, TILESET_WIDTH * zoom.get(), TILESET_HEIGHT * zoom.get(),
                                            0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());*/
            }

            private void redrawSelectedRect() {
                final GraphicsContext gContext = selectedRectCanvas.getGraphicsContext2D();
                gContext.clearRect(0, 0, selectedRectCanvas.getWidth(), selectedRectCanvas.getHeight());

                final Rectangle2D selRect = selectedRects[selectedLayer.get().ordinal()];
                gContext.strokeRoundRect(selRect.getMinX() * TILE_WIDTH * tilesetZoom.get(),
                                         selRect.getMinY() * TILE_HEIGHT * tilesetZoom.get(),
                                         selRect.getWidth() * TILE_WIDTH * tilesetZoom.get(),
                                         selRect.getHeight() * TILE_HEIGHT * tilesetZoom.get(), 10, 10);
            }

            private void fixSize() {
                tilesetCanvas.setWidth(TILESET_WIDTH * tilesetZoom.get());
                tilesetCanvas.setHeight(TILESET_HEIGHT * tilesetZoom.get());
            }

            /*
             * TODO:
             * Single PxAttrPopup instance that shown/hidden rather than
             * creating a new object every time the user right-clicks on
             * this Pane. Use selectedLayer and getX to figure out what
             * tile the user wants to view/change the attribute of.
             * Create an initPxAttrPopup() method?
             */
            private final class PxAttrPopup extends Popup {
                PxAttrPopup(final int layer, final int tilesetX, final int tilesetY) {
                    //TODO: Hide when parent MapEditTab is closed

                    setAutoHide(true);
                    setAutoFix(false);

                    final VBox content = new VBox(10);
                    content.setPadding(new Insets(5, 5, 5, 5));
                    FXUtil.INSTANCE.setBackgroundColor(content, Color.BLACK);

                    /* *************************************** Attribute Label ************************************** */
                    final PxAttr pxAttr = pxAttrs[layer];
                    final int[][] attributes = null == pxAttr ? null : pxAttr.getAttributes();

                    final Text attributeLabel = null == attributes ?
                                                new Text("No attributes for this tileset") :
                                                new Text(String.format("Current attribute: %02X",
                                                                       attributes[tilesetY][tilesetX]));
                    attributeLabel.setFont(Font.font(null, FontWeight.BOLD, 12));
                    attributeLabel.setFill(Color.WHITE);

                    /* *************************************** Attribute Image ************************************** */
                    final Image scaledImg = FXUtil.INSTANCE.scale(pxAttrImage, 2);
                    //div height by 2 to cut empty bottom half of image
                    final Canvas pxAttrCanvas = new Canvas(scaledImg.getWidth(), scaledImg.getHeight() / 2);
                    final GraphicsContext gContext = pxAttrCanvas.getGraphicsContext2D();

                    //div height by 2 to cut empty bottom half of image
                    gContext.drawImage(scaledImg,
                                       0, 0, scaledImg.getWidth(), scaledImg.getHeight() / 2,
                                       0, 0, pxAttrCanvas.getWidth(), pxAttrCanvas.getHeight());

                    //TODO: Grid is cut off at edges for some reason
                    //Draw grid over image
                    gContext.setStroke(Color.WHITE);
                    gContext.setLineWidth(1);
                    for (int y = 0; y < pxAttrCanvas.getHeight(); y += (PXATTR_TILE_HEIGHT * 2)) {
                        gContext.strokeLine(0, y, pxAttrCanvas.getWidth() - 1, y);
                    }
                    for (int x = 0; x < pxAttrCanvas.getWidth(); x += (PXATTR_TILE_WIDTH * 2)) {
                        gContext.strokeLine(x, 0, x, pxAttrCanvas.getHeight() - 1);
                    }

                    if (null != attributes) {
                        pxAttrCanvas.setOnMouseClicked(event -> {
                            if (MouseButton.PRIMARY == event.getButton()) {
                                //Image scaled up by 2
                                final int x = (int)event.getX() / 2 / PXATTR_TILE_WIDTH;
                                final int y = (int)event.getY() / 2 / PXATTR_TILE_HEIGHT;

                                try {
                                    final int attr = (y * PXATTR_TILES_PER_ROW) + x;
                                    PxAttrManager.setAttribute(head.getTilesetNames()[layer], tilesetX, tilesetY, attr);
                                    attributeLabel.setText(String.format("Current attribute: %02X\nHovered attribute: %02X",
                                                                         attr, attr));
                                }
                                catch (final IOException except) {
                                    //TODO: Better and more descriptive Alert
                                    FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR, "Failed to set attribute", null,
                                                                "Failed to set attribute").showAndWait();
                                }
                            }
                        });
                        pxAttrCanvas.setOnMouseMoved(event -> {
                            //Image scaled up by 2
                            final int x = (int)event.getX() / 2 / PXATTR_TILE_WIDTH;
                            final int y = (int)event.getY() / 2 / PXATTR_TILE_HEIGHT;
                            attributeLabel.setText(String.format("Current attribute: %02X\nHovered attribute: %02X",
                                                                 pxAttr.getAttributes()[tilesetY][tilesetX],
                                                                 (y * PXATTR_TILES_PER_ROW) + x));
                        });
                    }

                    content.getChildren().addAll(pxAttrCanvas, attributeLabel);
                    getContent().add(content);
                }
            }
        }

        //TODO: Undocking (similar to tilesetStage)
        private final class EntityPane extends SplitPane {
            private final EntityEditorPane editorPane;
            private final ListView <Integer> entityList;

            EntityPane() {
                editorPane = new EntityEditorPane();

                entityList = new ListView <>();
                entityList.setCellFactory(listView -> new ListCell <Integer>() {
                    private final ImageView entityImageView = new ImageView();

                    @Override
                    public void updateItem(final Integer entityIndex, final boolean empty) {
                        super.updateItem(entityIndex, empty);
                        if (empty) {
                            setText(null);
                            //setGraphic(null);
                            entityImageView.setImage(null);
                            return;
                        }

                        //TODO: Pull from spritesheets (when I figure out framerects)
                        final int x = (entityIndex % ENTITIES_PER_ROW) * ENTITY_WIDTH;
                        final int y = (entityIndex / ENTITIES_PER_ROW) * ENTITY_HEIGHT;

                        entityImageView.setImage(FXUtil.INSTANCE.scale(new WritableImage(entityImage.getPixelReader(), x, y,
                                                                                         ENTITY_WIDTH, ENTITY_HEIGHT), 2));
                        setText(String.format("#%d - " + entityNames[entityIndex], entityIndex));
                        setGraphic(entityImageView);
                    }
                });
                entityList.setOnMouseClicked(event -> {
                    if (MouseButton.PRIMARY == event.getButton() && 2 == event.getClickCount()) {
                        //TODO: Redraw entity
                        editorPane.getEntity().setType(entityList.getSelectionModel().getSelectedItem());
                        markChanged();
                    }
                });
                entityList.setDisable(true);

                for (int i = 0; i < entityNames.length; ++i) {
                    entityList.getItems().add(i);
                }

                setOrientation(Orientation.VERTICAL);
                setDividerPositions(0.2);
                getItems().addAll(editorPane, entityList);
            }

            void setEntity(final PxPack.Entity entity) {
                editorPane.setEntity(entity);
                entityList.setDisable(null == entity);

                //TODO: Sometimes the types exceed what seems to be the limit, so handle that
                if (null == entity) {
                    entityList.getSelectionModel().clearSelection();
                    entityList.scrollTo(0);
                }
                else {
                    entityList.getSelectionModel().select(entity.getType());
                    entityList.scrollTo(entity.getType());
                }
            }

            private final class EntityEditorPane extends GridPane {
                //TODO: Allow editing draw order (index in entities list)
                private PxPack.Entity currentlyEditingEntity;
                private final TextField nameField;

                EntityEditorPane() {
                    setPadding(new Insets(10, 10, 10, 10));
                    setVgap(10);
                    setHgap(20);

                    final Font font = Font.font(null, FontWeight.NORMAL, 12);

                    final Text label = new Text("Name");
                    label.setFont(font);
                    add(label, 0, 0);

                    nameField = new TextField();
                    FXUtil.INSTANCE.setMaxLen(nameField, PxPack.Entity.NAME_MAX_LEN);
                    nameField.textProperty().addListener((observable, oldValue, newValue) -> {
                        /*
                         * textProperty() will also change if the user selects a
                         * different entity, so make sure that the reason it changed
                         * was because the user typed something in. Otherwise we're
                         * needlessly setting the name and marking the tab as changed
                         * when no changes were made
                         */
                        if (nameField.isFocused()) {
                            //TODO: Redraw entity
                            currentlyEditingEntity.setName(newValue);
                            markChanged();
                        }
                    });
                    nameField.setDisable(true);

                    add(nameField, 1, 0);
                }

                PxPack.Entity getEntity() {
                    return currentlyEditingEntity;
                }

                void setEntity(final PxPack.Entity entity) {
                    currentlyEditingEntity = entity;
                    nameField.setDisable(null == entity);
                    nameField.setText(null == entity ? "" : entity.getName());
                }
            }
        }

        private final class MapPane extends ScrollPane {
            private final SimpleObjectProperty <Color> bgColor;

            private final PxPack.TileLayer[] tileLayers;

            private final Canvas[] mapCanvases;
            //private final Canvas tileTypesCanvas;
            private final Canvas entityCanvas;

            private final Canvas gridCanvas;
            private final Canvas cursorCanvas;

            private final StackPane stackPane;

            MapPane() {
                tileLayers = map.getTileLayers();

                /* ******************************************** Canvases ******************************************** */
                mapCanvases = new Canvas[tileLayers.length];
                for (int i = 0; i < mapCanvases.length; ++i) {
                    mapCanvases[i] = new Canvas();
                    mapCanvases[i].setVisible(displayedLayers.get().contains(Layer.values()[i]));
                }

                //Always visible - what is drawn is changed when viewSettings changes
                entityCanvas = new Canvas();
                entityCanvas.getGraphicsContext2D().setStroke(Color.LIME);
                entityCanvas.getGraphicsContext2D().setFill(Color.WHITE);
                entityCanvas.getGraphicsContext2D().setLineWidth(2);

                gridCanvas = new Canvas();
                gridCanvas.setVisible(viewSettings.get().contains(ViewOption.GRID));
                gridCanvas.getGraphicsContext2D().setStroke(Color.WHITE);
                gridCanvas.getGraphicsContext2D().setLineWidth(1);

                cursorCanvas = new Canvas();
                cursorCanvas.setVisible(EditMode.TILE == editMode.get());
                cursorCanvas.getGraphicsContext2D().setStroke(Color.WHITE);
                cursorCanvas.getGraphicsContext2D().setLineWidth(2);

                fixCanvasSizes();
                redrawGridLayer();
                redrawEntityLayer();

                /* ******************************************** StackPane ******************************************* */
                stackPane = new StackPane();
                stackPane.setAlignment(Pos.TOP_LEFT);
                for (int i = mapCanvases.length - 1; i >= 0; --i) {
                    stackPane.getChildren().add(mapCanvases[i]);
                }
                stackPane.getChildren().addAll(entityCanvas, gridCanvas, cursorCanvas);

                bgColor = new SimpleObjectProperty <>(head.getBgColor());
                bgColor.addListener((observable, oldValue, newValue) -> FXUtil.INSTANCE.setBackgroundColor(stackPane, newValue));
                FXUtil.INSTANCE.setBackgroundColor(stackPane, bgColor.get());

                initEventHandlers();

                setPannable(false);
                setContextMenu(initContextMenu());
                setContent(stackPane);
            }

            /**
             * Binds and initializes {@code EventHandlers} for clicks
             * on the {@code Canvases} and {@code Panes} as well as
             * changes to the various static Simple_*_Properties
             * initialized and controlled by the MapEditTab class.
             */
            private void initEventHandlers() {
                displayedLayers.addListener((observable, oldValue, newValue) -> {
                    for (int i = 0; i < Layer.values().length; ++i) {
                        mapCanvases[i].setVisible(newValue.contains(Layer.values()[i]));
                    }
                });

                selectedLayer.addListener((observable, oldValue, newValue) -> {
                    if (viewSettings.get().contains(ViewOption.TILE_TYPES)) {
                        redrawTileLayer(oldValue.ordinal()); //"undraw" tiletypes from previously selected layer
                        redrawTileLayer(newValue.ordinal()); //draw tiletypes onto new selected layer
                    }
                });

                viewSettings.addListener((observable, oldValue, newValue) -> {
                    //Only one flag is changed at a time
                    //TODO: Find more efficient method (complement? noneOf?)
                    if ((oldValue.contains(ViewOption.TILE_TYPES) && !newValue.contains(ViewOption.TILE_TYPES)) ||
                        (!oldValue.contains(ViewOption.TILE_TYPES) && newValue.contains(ViewOption.TILE_TYPES))) {
                        redrawTileLayer(selectedLayer.get().ordinal());
                    }
                    else if ((oldValue.contains(ViewOption.GRID) && !newValue.contains(ViewOption.GRID)) ||
                             (!oldValue.contains(ViewOption.GRID) && newValue.contains(ViewOption.GRID))) {
                        gridCanvas.setVisible(newValue.contains(ViewOption.GRID));
                    }
                    //If any of the entity flags changed
                    //TODO: Is the if necessary or can I just leave it as an else?
                    else if (EditMode.ENTITY != editMode.get() &&
                             (((oldValue.contains(ViewOption.ENTITY_BOXES) && !newValue.contains(ViewOption.ENTITY_BOXES)) ||
                               (!oldValue.contains(ViewOption.ENTITY_BOXES) && newValue.contains(ViewOption.ENTITY_BOXES))) ||

                              ((oldValue.contains(ViewOption.ENTITY_SPRITES) && !newValue.contains(ViewOption.ENTITY_SPRITES)) ||
                               (!oldValue.contains(ViewOption.ENTITY_SPRITES) && newValue.contains(ViewOption.ENTITY_SPRITES))) ||

                              ((oldValue.contains(ViewOption.ENTITY_NAMES) && !newValue.contains(ViewOption.ENTITY_NAMES)) ||
                               (!oldValue.contains(ViewOption.ENTITY_NAMES) && newValue.contains(ViewOption.ENTITY_NAMES))))) {
                        redrawEntityLayer();
                    }
                });

                editMode.addListener((observable, oldValue, newValue) -> {
                    redrawEntityLayer();
                    cursorCanvas.setVisible(EditMode.TILE == newValue);
                });

                mapZoom.addListener((observable, oldValue, newValue) -> {
                    //TODO: redraw cursor so it fixes size immediately
                    fixCanvasSizes();
                    redrawAllTileLayers();
                    redrawEntityLayer();
                    redrawGridLayer();
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

                            //TODO: Remove coords from title on tab close and when this is hidden
                            final String title = KeroEdit.getTitle();
                            final int coordsIndex = title.indexOf(" - ("); //TODO: Use regex for - (x, y)
                            if (-1 == coordsIndex) {
                                KeroEdit.setTitle(title + " - (" + x + ", " + y + ')');
                            }
                            else {
                                KeroEdit.setTitle(title.substring(0, coordsIndex) + " - (" + x + ", " + y + ')');
                            }

                            final GraphicsContext cursorGContext = cursorCanvas.getGraphicsContext2D();
                            cursorGContext.clearRect(0, 0, cursorCanvas.getWidth(), cursorCanvas.getHeight());

                            switch (drawMode.get()) {
                                case DRAW:
                                    final int[][] tilesRect = selectedTiles[selectedLayer.get().ordinal()];
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
                //Note to self - don't use cursorCanvas::fireEvent - causes StackOverflowException (infinite recursion I think)
                cursorCanvas.setOnMouseDragged(cursorCanvas.getOnMouseMoved());

                stackPane.setOnMouseClicked(event -> {
                    if (EditMode.TILE == editMode.get()) {
                        try {
                            FXUtil.INSTANCE.task(() -> {
                                final int layer = selectedLayer.get().ordinal();

                                if (mapCanvases[layer].isVisible() && MouseButton.PRIMARY == event.getButton()) {
                                    final int[][] tiles = tileLayers[layer].getTiles();

                                    if (null != tiles) {
                                        //grabs x & y and bounds them to be within map
                                        final int x = (int)(UtilsKt.bound((int)event.getX(), 0,
                                                                          (int)(mapCanvases[layer].getWidth() - 1)) /
                                                            mapZoom.get() / TILE_WIDTH);
                                        final int y = (int)(UtilsKt.bound((int)event.getY(), 0,
                                                                          (int)(mapCanvases[layer].getHeight() - 1)) /
                                                            mapZoom.get() / TILE_HEIGHT);

                                        /*
                                         * Caps newTiles.length in the event that x or y is close enough
                                         * to the map's edge that selectedTiles goes off the edge
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

                                        if (!oldEqualsNew) {
                                            addUndo(new UndoableMapDrawEdit(layer, x, y, oldTiles, newTiles));
                                        }
                                    }
                                }
                                return null;
                            }).run();
                        }
                        catch (final Exception except) {
                            Logger.INSTANCE.logThrowable("Exception in stackPane.setOnMouseClicked()", except);
                        }
                    }
                });
                //Note to self - don't use stackPane::fireEvent - causes StackOverflowException (infinite recursion I think)
                //TODO: MouseDrag has its own handler as it will need one (e.g. for DrawMode.RECT)
                stackPane.setOnMouseDragged(stackPane.getOnMouseClicked());

                entityCanvas.setOnMouseClicked(event -> {
                    if (EditMode.ENTITY == editMode.get()) {
                        //TODO: Do I need to use MathUtil.bound() for this?

                        final int x = (int)(event.getX() / mapZoom.get() / TILE_WIDTH);
                        final int y = (int)(event.getY() / mapZoom.get() / TILE_HEIGHT);

                        for (final PxPack.Entity e : entities) {
                            if (x == e.getX() && y == e.getY()) {
                                //TODO: Draw box or something around selected entity
                                entityPane.setEntity(e);
                                return;
                            }
                        }

                        entityPane.setEntity(null);
                    }
                });

                gridCanvas.setOnMouseClicked(event -> {
                    if (EditMode.ENTITY == editMode.get()) {
                        entityCanvas.fireEvent(event);
                    }
                });
            }

            /**
             * Initializes the {@code ContextMenu} for this {@code ScrollPane}
             *
             * @return The created {@code ContextMenu}
             */
            private ContextMenu initContextMenu() {
                final MenuItem[] menuItems = {new MenuItem(Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.MENU_TEXT")),
                                              new MenuItem(Messages.INSTANCE.getString("MapEditTab.TileEditTab.BgColor.MENU_TEXT"))};

                menuItems[MapPaneMenuItem.RESIZE.ordinal()].setOnAction(event -> {
                    final int layer = selectedLayer.get().ordinal();

                    final String layerName;
                    switch (layer) {
                        case 0:
                            layerName = Messages.INSTANCE.getString("PxPack.LayerNames.FOREGROUND");
                            break;
                        case 1:
                            layerName = Messages.INSTANCE.getString("PxPack.LayerNames.MIDDLEGROUND");
                            break;
                        case 2:
                        default:
                            layerName = Messages.INSTANCE.getString("PxPack.LayerNames.BACKGROUND");
                    }

                    final String title = MessageFormat.format(Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.TITLE"),
                                                              layerName);

                    final int[][] oldTiles = mapPane.tileLayers[layer].getTiles();
                    final int oldWidth = null == oldTiles ? 0 : oldTiles[0].length;
                    final int oldHeight = null == oldTiles ? 0 : oldTiles.length;

                    final String currentSizeStr = MessageFormat.format(Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.CURRENT_SIZE"),
                                                                       oldWidth, oldHeight);

                    FXUtil.INSTANCE.createDualTextFieldDialog(title, currentSizeStr,
                                                              Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.NEW_WIDTH"),
                                                              Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.NEW_HEIGHT"))
                                   .showAndWait().ifPresent(result -> {
                        final String widthStr = result.component1();
                        final String heightStr = result.component2();

                        if (0 < widthStr.length() && 0 < heightStr.length()) {
                            final int newWidth;
                            final int newHeight;
                            try {
                                newWidth = Integer.parseInt(widthStr);
                                newHeight = Integer.parseInt(heightStr);
                            }
                            catch (final NumberFormatException except) {
                                FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                            Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.NumFormatExcept.TITLE"),
                                                            null,
                                                            Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.NumFormatExcept.MESSAGE"))
                                               .showAndWait();
                                return;
                            }

                            if (newWidth > 0xFFFF || newHeight > 0xFFFF) {
                                FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                            Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.TITLE"),
                                                            null,
                                                            Messages.INSTANCE.getString("MapEditTab.TileEditTab.Resize.InvalidDimensions.MESSAGE"))
                                               .showAndWait();
                                return;
                            }

                            tileLayers[layer].resize(newWidth, newHeight);

                            fixCanvasSizes();
                            redrawTileLayer(layer);
                            redrawGridLayer();

                            addUndo(new UndoableMapResizeEdit(layer, oldTiles, tileLayers[layer].getTiles()));
                        }
                    });
                });

                menuItems[MapPaneMenuItem.BG_COLOR.ordinal()].setOnAction(event -> {
                    final ColorPicker cPicker = new ColorPicker(mapPane.bgColor.get());
                    cPicker.setOnAction(ev -> {
                        if (!cPicker.getValue().isOpaque()) {
                            FXUtil.INSTANCE.createAlert(Alert.AlertType.ERROR,
                                                        Messages.INSTANCE.getString("MapEditTab.TileEditTab.BgColor.OpacityError.TITLE"),
                                                        null,
                                                        Messages.INSTANCE.getString("MapEditTab.TileEditTab.BgColor.OpacityError.MESSAGE"))
                                           .showAndWait();
                        }
                        else {
                            head.setBgColor(cPicker.getValue());
                            bgColor.set(cPicker.getValue());
                            markChanged();
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
             * @param x     The x coordinate of the tile to redraw
             * @param y     The y coordinate of the tile to redraw
             */
            private void redrawTile(final int layer, final int x, final int y) {
                try {
                    //TODO: Create new Service subclass that takes layer, x, and y params in constructor and can be restarted on demand?
                    FXUtil.INSTANCE.task(() -> {
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

                        PixelReader pxAttrImgReader;
                        final Image tileTypeImg;
                        int[][] attributes;
                        final boolean drawTileType;

                        if (viewSettings.get().contains(ViewOption.TILE_TYPES) && selectedLayer.get().ordinal() == layer) {
                            final PxAttr pxAttr = tilesetPane.pxAttrs[selectedLayer.get().ordinal()];
                            attributes = pxAttr == null ? null : pxAttr.getAttributes();
                            pxAttrImgReader = pxAttrImage.getPixelReader();
                            if (!(drawTileType = (null != attributes && null != pxAttrImgReader))) {
                                attributes = null;
                                pxAttrImgReader = null;
                            }
                        }
                        else {
                            attributes = null;
                            pxAttrImgReader = null;
                            drawTileType = false;
                        }

                        final int tilesetX = tileIndex % TILES_PER_ROW;
                        final int tilesetY = tileIndex / TILES_PER_ROW;

                        tileImg = FXUtil.INSTANCE.scale(new WritableImage(tilesetReader,
                                                                          tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT,
                                                                          TILE_WIDTH, TILE_HEIGHT), mapZoom.get());
                        if (drawTileType) {
                            final int attributesX = attributes[tilesetY][tilesetX] % PXATTR_TILES_PER_ROW;
                            final int attributesY = attributes[tilesetY][tilesetX] / PXATTR_TILES_PER_ROW;

                            tileTypeImg = FXUtil.INSTANCE.scale(new WritableImage(pxAttrImgReader,
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
                            final GraphicsContext gContext = mapCanvases[layer].getGraphicsContext2D();

                            final double calcX = x * TILE_WIDTH * mapZoom.get();
                            final double calcY = y * TILE_HEIGHT * mapZoom.get();

                            gContext.clearRect(calcX, calcY, tileImg.getWidth(), tileImg.getHeight());

                            gContext.drawImage(tileImg, calcX, calcY);

                            //null images ignored
                            gContext.drawImage(tileTypeImg, calcX, calcY);
                        });

                        return null;
                    }).run();
                }
                catch (final Exception except) {
                    Logger.INSTANCE.logThrowable("Exception in redrawTile(" + layer + ", " + x + ", " + y + ", " + ')', except);
                }
            }

            /**
             * Redraws an entire given layer, including tile types if they are set to be visible.
             *
             * @param layer The layer to redraw
             */
            private void redrawTileLayer(final int layer) {
                try {
                    //TODO: Create new Service subclass that takes layer param in constructor and can be restarted on demand?
                    FXUtil.INSTANCE.task(() -> {
                        final int[][] tiles = tileLayers[layer].getTiles();
                        if (null == tiles) {
                            return null;
                        }

                        final PixelReader tilesetReader = tilesetPane.tilesets[layer].getPixelReader();
                        if (null == tilesetReader) { //this should handle empty/nonexistent images
                            return null;
                        }

                        final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                        final WritableImage layerImg = new WritableImage(tiles[0].length * TILE_WIDTH,
                                                                         tiles.length * TILE_HEIGHT);
                        final PixelWriter layerImgWriter = layerImg.getPixelWriter();

                        /* *************************************** Tile Types *************************************** */
                        PixelReader pxAttrImgReader = null;
                        WritableImage tmpTileTypeImg = null;
                        PixelWriter tileTypeImgWriter = null;
                        int[][] attributes = null;
                        byte[] attrTile = null;

                        boolean drawTileTypes = false;

                        if (viewSettings.get().contains(ViewOption.TILE_TYPES) && selectedLayer.get().ordinal() == layer) {
                            final PxAttr pxAttr = tilesetPane.pxAttrs[selectedLayer.get().ordinal()];
                            attributes = pxAttr == null ? null : pxAttr.getAttributes();
                            pxAttrImgReader = pxAttrImage.getPixelReader();

                            if (drawTileTypes = (null != attributes && null != pxAttrImgReader)) {
                                tmpTileTypeImg = new WritableImage((int)layerImg.getWidth() * 2,
                                                                   (int)layerImg.getHeight() * 2);
                                tileTypeImgWriter = tmpTileTypeImg.getPixelWriter();

                                attrTile = new byte[PXATTR_TILE_WIDTH * PXATTR_TILE_HEIGHT * 4];
                            }
                            else {
                                attributes = null;
                                pxAttrImgReader = null;
                            }
                        }

                        final byte[] tile = new byte[TILE_WIDTH * TILE_HEIGHT * 4];

                        for (int y = 0; y < tiles.length; ++y) {
                            for (int x = 0; x < tiles[y].length; ++x) {
                                /* ************************************* Tiles ************************************** */
                                final int tilesetX = tiles[y][x] % TILES_PER_ROW;
                                final int tilesetY = tiles[y][x] / TILES_PER_ROW;

                                tilesetReader.getPixels(tilesetX * TILE_WIDTH, tilesetY * TILE_HEIGHT,
                                                        TILE_WIDTH, TILE_HEIGHT, pxFormat, tile, 0, TILE_WIDTH * 4);

                                layerImgWriter.setPixels(x * TILE_WIDTH, y * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT,
                                                         pxFormat, tile, 0, TILE_WIDTH * 4);

                                /* *********************************** Tile Types *********************************** */
                                if (drawTileTypes) {
                                    final int attributesX = attributes[tilesetY][tilesetX] % PXATTR_TILES_PER_ROW;
                                    final int attributesY = attributes[tilesetY][tilesetX] / PXATTR_TILES_PER_ROW;

                                    pxAttrImgReader.getPixels(attributesX * PXATTR_TILE_WIDTH,
                                                              attributesY * PXATTR_TILE_HEIGHT,
                                                              PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT, pxFormat,
                                                              attrTile, 0, PXATTR_TILE_WIDTH * 4);

                                    tileTypeImgWriter.setPixels(x * PXATTR_TILE_WIDTH,
                                                                y * PXATTR_TILE_HEIGHT,
                                                                PXATTR_TILE_WIDTH, PXATTR_TILE_HEIGHT,
                                                                pxFormat, attrTile, 0, PXATTR_TILE_WIDTH * 4);
                                }
                            }
                        }

                        final Image typeImg = FXUtil.INSTANCE.scale(tmpTileTypeImg, mapZoom.get() / 2);
                        Platform.runLater(() -> {
                            final GraphicsContext layerGContext = mapCanvases[layer].getGraphicsContext2D();

                            layerGContext.clearRect(0, 0, mapCanvases[layer].getWidth(), mapCanvases[layer].getHeight());

                            layerGContext.drawImage(FXUtil.INSTANCE.scale(layerImg, mapZoom.get()), 0, 0);
                            //null images ignored
                            layerGContext.drawImage(typeImg, 0, 0);
                        });

                        return null;
                    }).run();
                }
                catch (final Exception except) {
                    Logger.INSTANCE.logThrowable("Exception in redrawTileLayer(" + layer + ')', except);
                }
            }

            /**
             * Redraws all layers, including tile types if they are set to be visible.
             */
            private void redrawAllTileLayers() {
                for (int i = 0; i < tileLayers.length; ++i) {
                    redrawTileLayer(i);
                }
            }

            private void redrawEntityLayer() {
                try {
                    FXUtil.INSTANCE.task(() -> {
                        //TODO: Two of these just call Platform.runLater(), so should only sprite drawing go in a Task?

                        //TODO: Single image that is modified, then scale and drawn onto Canvas?
                        final GraphicsContext gContext = entityCanvas.getGraphicsContext2D();

                        Platform.runLater(() -> gContext.clearRect(0, 0, entityCanvas.getWidth(),
                                                                   entityCanvas.getHeight()));

                        /* ************************************* Entity Sprites ************************************* */
                        if (EditMode.ENTITY == editMode.get() || viewSettings.get().contains(ViewOption.ENTITY_SPRITES)) {
                            final PixelReader entitiesImgReader = entityImage.getPixelReader();
                            if (null != entitiesImgReader) {
                                final WritablePixelFormat <ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

                                //TODO: Verify all of this is right and stuff
                                //TODO: More elegant width/height solution?

                                /*
                                 * entitiesImg is unscaled (as per mapZoom), but the size
                                 * of an entity in entityImage is 16px by 16px, which is
                                 * double that of tiles in a tileset, which are 8px by 8px.
                                 */
                                final WritableImage entitiesImg =
                                        new WritableImage((int)(entityCanvas.getWidth() / mapZoom.get()) *
                                                          (ENTITY_WIDTH / TILE_WIDTH),
                                                          (int)(entityCanvas.getHeight() / mapZoom.get()) *
                                                          (ENTITY_HEIGHT / TILE_HEIGHT));
                                final PixelWriter entitiesImgWriter = entitiesImg.getPixelWriter();

                                final byte[] entityBuf = new byte[ENTITY_WIDTH * ENTITY_HEIGHT * 4];

                                for (final PxPack.Entity e : entities) {
                                    final int index = e.getType();
                                    final int imgX = (index % ENTITIES_PER_ROW) * ENTITY_WIDTH;
                                    final int imgY = (index / ENTITIES_PER_ROW) * ENTITY_HEIGHT;

                                    entitiesImgReader.getPixels(imgX, imgY, ENTITY_WIDTH, ENTITY_HEIGHT,
                                                                pxFormat, entityBuf, 0, ENTITY_WIDTH * 4);

                                    entitiesImgWriter.setPixels(e.getX() * ENTITY_WIDTH, e.getY() * ENTITY_HEIGHT,
                                                                ENTITY_WIDTH, ENTITY_HEIGHT,
                                                                pxFormat, entityBuf, 0, ENTITY_WIDTH * 4);
                                }

                                Platform.runLater(() -> gContext.drawImage(FXUtil.INSTANCE.scale(entitiesImg,
                                                                                                 mapZoom.get() /
                                                                                                 (ENTITY_WIDTH / TILE_WIDTH)),
                                                                           0, 0));
                            }
                        }

                        /* ************************************** Entity Boxes ************************************** */
                        if (EditMode.ENTITY == editMode.get() || viewSettings.get().contains(ViewOption.ENTITY_BOXES)) {
                            Platform.runLater(() -> {
                                for (final PxPack.Entity e : entities) {
                                    gContext.strokeRect(e.getX() * TILE_WIDTH * mapZoom.get(),
                                                        e.getY() * TILE_HEIGHT * mapZoom.get(),
                                                        TILE_WIDTH * mapZoom.get(), TILE_HEIGHT * mapZoom.get());
                                }
                            });
                        }

                        /*
                         * TODO:
                         * Text is drawn above the entity - change the baseline attribute to fix this
                         * Split text onto multiple lines as needed rather than squising it
                         * Redraw layer (at least names) when a name is changed
                         * Change size as mapZoom changes?
                         */
                        /* ************************************** Entity Names ************************************** */
                        /*if (EditMode.ENTITY == editMode.get() || viewSettings.get().contains(ViewOption.ENTITY_NAMES)) {
                            Platform.runLater(() -> {
                                for (final PxPack.Entity e : entities) {
                                    gContext.fillText(e.getName(),
                                                      e.getX() * TILE_WIDTH * mapZoom.get(),
                                                      e.getY() * TILE_HEIGHT * mapZoom.get(),
                                                      TILE_WIDTH * mapZoom.get());
                                }
                            });
                        }*/

                        return null;
                    }).run();
                }
                catch (final Exception except) {
                    Logger.INSTANCE.logThrowable("Exception in redrawEntityLayer()", except);
                }
            }

            private void redrawGridLayer() {
                final GraphicsContext gContext = gridCanvas.getGraphicsContext2D();
                gContext.clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());

                for (int y = 0; y < gridCanvas.getHeight(); y += (TILE_HEIGHT * mapZoom.get())) {
                    gContext.strokeLine(0, y, gridCanvas.getWidth() - 1, y);
                }
                for (int x = 0; x < gridCanvas.getWidth(); x += (TILE_WIDTH * mapZoom.get())) {
                    gContext.strokeLine(x, 0, x, gridCanvas.getHeight() - 1);
                }
            }

            /**
             * Fixes the sizes of the {@code Canvas}es
             */
            private void fixCanvasSizes() {
                double maxWidth = 0, maxHeight = 0;
                for (int i = 0; i < tileLayers.length; ++i) {
                    final int[][] tiles = tileLayers[i].getTiles();
                    if (null == tiles) {
                        mapCanvases[i].setWidth(0);
                        mapCanvases[i].setHeight(0);
                        continue;
                    }

                    final double width = tiles[0].length * TILE_WIDTH * mapZoom.get();
                    final double height = tiles.length * TILE_HEIGHT * mapZoom.get();

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

                UndoableMapDrawEdit(final int layer, final int x, final int y,
                                    final int[][] oldTiles, final int[][] newTiles) {
                    NullArgumentException.Companion.requireNonNull(oldTiles, "UndoableMapDrawEdit", "oldTiles");
                    NullArgumentException.Companion.requireNonNull(newTiles, "UndoableMapDrawEdit", "newTiles");

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

                    redrawTileLayer(layer);
                    fixCanvasSizes();
                    redrawTileLayer(layer);
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

                    redrawTileLayer(layer);
                    fixCanvasSizes();
                    redrawTileLayer(layer);
                }
            }
        }
    }

    private final class PropertyEditTab extends FileEditTab {
        private final PxPack.Head head;

        PropertyEditTab() {
            super(MapEditTab.this.getPath(), Messages.INSTANCE.getString("MapEditTab.PropertyEditTab.TITLE"));
            head = map.getHead();
            setContent(initEditorPane());
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

        @Override
        protected void markChanged() {
            super.markChanged();
            MapEditTab.this.markChanged();
        }

        //Made public so the parent MapEditTab can call it in save()
        @Override
        public void markUnchanged() {
            super.markUnchanged();
        }

        private GridPane initEditorPane() {
            //TODO: For values that can be blank, put a "Clear" button next to them that sets the value to blank and selects a blank item

            final GridPane gPane = new GridPane();
            gPane.setPadding(new Insets(10, 10, 10, 10));
            gPane.setVgap(10);
            gPane.setHgap(20);

            final Font font = Font.font(null, FontWeight.NORMAL, 12);

            /* ******************************************* ComboBox Labels ****************************************** */
            final ArrayList <Text> labels = new ArrayList <>(1 + PxPack.Head.NUM_REF_MAPS + 1 + PxPack.NUM_LAYERS);
            for (int i = 0; i < PxPack.Head.NUM_REF_MAPS; ++i) {
                labels.add(new Text("Mapname " + (i + 1)));
            }
            labels.add(new Text("Spritesheet"));
            for (int i = 0; i < PxPack.NUM_LAYERS; ++i) {
                labels.add(new Text("Tileset " + (i + 1)));
            }

            for (final Text label : labels) {
                label.setFont(font);
            }

            /* ******************************************** Description ********************************************* */
            final Text descriptionLabel = new Text("Description");
            final TextField descriptionTextField = new TextField(head.getDescription());
            FXUtil.INSTANCE.setMaxLen(descriptionTextField, PxPack.Head.DESCRIPTION_MAX_LEN);
            descriptionTextField.textProperty().addListener((observable, oldValue, newValue) -> {
                head.setDescription(newValue);
                markChanged();
                tooltip.setText(map.getPath().toString() + '\n' +
                                Messages.INSTANCE.getString("MapEditTab.TOOLTIP_DESCRIPTION_LABEL") + newValue);
            });

            final ArrayList <ComboBox <Path>> fields = new ArrayList <>(labels.size());

            /* ********************************************** Mapnames ********************************************** */
            final String[] currentMapNames = head.getMapNames();
            for (int i = 0; i < PxPack.Head.NUM_REF_MAPS; ++i) {
                //TODO: Add option to leave value blank
                final ComboBox <Path> cBox = new ComboBox <>(GameData.INSTANCE.getMapList());

                //TODO: Can I make one Callback object that is used for multiple ComboBoxes?
                cBox.setCellFactory(listView -> new ListCell <Path>() {
                    @Override
                    public void updateItem(final Path map, final boolean empty) {
                        super.updateItem(map, empty);
                        setText(empty ? null : UtilsKt.baseFilename(map, GameData.mapExtension));
                    }
                });

                //TODO: Set the Button Cell of the ComboBox to have the filename as the label, not the full path

                final Path map = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                           File.separatorChar + GameData.mapFolder + File.separatorChar +
                                           currentMapNames[i] + GameData.mapExtension);

                final SingleSelectionModel <Path> selectModel = cBox.getSelectionModel();
                selectModel.select(map);
                final int index = i;
                selectModel.selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                    /*
                     * newValue is null when the map list is cleared when
                     * a new mod is loaded in KeroEdit. Ideally this Tab
                     * object would be destroyed when closed and newValue
                     * would never be null, so just blame the JVM's rare
                     * use of The Reaper (GC)
                     */
                    if (null != newValue) {
                        head.setMapname(index, UtilsKt.baseFilename(newValue, GameData.mapExtension));
                        markChanged();
                    }
                });

                fields.add(cBox);
            }

            /* ********************************************* Spritesheet ******************************************** */
            {
                //TODO: Check if spritesheet can be blank - if so, put blank item into list (or button that clears selection or something)
                final ComboBox <Path> cBox = new ComboBox <>(GameData.INSTANCE.getImageList());

                cBox.setCellFactory(comboBox -> new ListCell <Path>() {
                    @Override
                    public void updateItem(final Path spritesheet, final boolean empty) {
                        super.updateItem(spritesheet, empty);
                        setText(empty ? null : UtilsKt.baseFilename(spritesheet, GameData.imageExtension));
                    }
                });

                final Path spritesheet = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                                   File.separatorChar + GameData.imageFolder + File.separatorChar +
                                                   head.getSpritesheetName() + GameData.imageExtension);

                final SingleSelectionModel <Path> selectModel = cBox.getSelectionModel();
                selectModel.select(spritesheet);
                selectModel.selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                    head.setSpritesheetName(UtilsKt.baseFilename(newValue, GameData.imageExtension));
                    markChanged();
                    /*
                     * TODO:
                     * When I start pulling entity sprites directly from
                     * spritesheets, redraw entities in TileEditTab.
                     */
                });

                fields.add(cBox);
            }

            /* ************************************************ Data ************************************************ */
            //TODO: Data

            /* *********************************************** Tilesets ********************************************** */
            final String[] currentTilesetNames = head.getTilesetNames();
            for (int i = 0; i < PxPack.NUM_LAYERS; ++i) {
                final ComboBox <Path> cBox = new ComboBox <>(GameData.INSTANCE.getImageList());

                cBox.setCellFactory(comboBox -> new ListCell <Path>() {
                    @Override
                    public void updateItem(final Path tileset, final boolean empty) {
                        super.updateItem(tileset, empty);
                        setText(empty ? null : UtilsKt.baseFilename(tileset, GameData.imageExtension));
                    }
                });

                final Path tileset = Paths.get(GameData.INSTANCE.getResourceFolder().toString() +
                                               File.separatorChar + GameData.imageFolder + File.separator +
                                               currentTilesetNames[i] + GameData.imageExtension);

                final SingleSelectionModel <Path> selectModel = cBox.getSelectionModel();
                selectModel.select(tileset);
                final int index = i;
                selectModel.selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                    head.setTilesetName(index, UtilsKt.baseFilename(newValue, GameData.imageExtension));
                    markChanged();

                    //TODO: Only reload affected tileset, not all of them
                    tileEditTab.tilesetPane.loadTilesets.restart();
                    tileEditTab.tilesetPane.loadPxAttrs.restart();
                });

                fields.add(cBox); //TODO: Add option to leave second two tilesets blank
            }

            /* ****************************************** Visibility Types ****************************************** */
            //TODO: Visibility types

            /* ******************************************** Scroll Types ******************************************** */
            //TODO: Scroll types

            int y = 0;
            gPane.add(descriptionLabel, 0, y);
            gPane.add(descriptionTextField, 1, y++);

            for (int i = 0; i < labels.size(); ++i, ++y) {
                gPane.add(labels.get(i), 0, y);
                gPane.add(fields.get(i), 1, y);
            }

            return gPane;
        }
    }

    public enum DrawMode implements SafeEnum <DrawMode> {
        DRAW,
        RECT,
        COPY,
        FILL,
        REPLACE
    }

    public enum Layer implements SafeEnum <Layer> {
        FOREGROUND,
        MIDDLEGROUND,
        BACKGROUND
    }

    public enum ViewOption implements SafeEnum <ViewOption> {
        TILE_TYPES,
        GRID,
        ENTITY_BOXES,
        ENTITY_SPRITES,
        ENTITY_NAMES
    }

    public enum EditMode implements SafeEnum <EditMode> {
        TILE,
        ENTITY
    }

    private enum MapPaneMenuItem implements SafeEnum <MapPaneMenuItem> {
        RESIZE,
        BG_COLOR
    }
}