/*
 * TODO:
 * Default initialize strings as empty? (files known to pre-exist for filenames?)
 * Make rename() undoable?
 * New map constructor?
 */

package io.fdeitylink.keroedit.map;

import java.util.ArrayList;
import java.util.Arrays;

import java.text.MessageFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.nio.channels.SeekableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.text.ParseException;

import java.io.UnsupportedEncodingException;

import javafx.scene.paint.Color;

import io.fdeitylink.keroedit.Messages;

/**
 * Object for storing information about a PXPACK map file
 */
public final class PxPack {
    public static final int NUM_LAYERS = 3;

    private Path mapPath;

    private /*final*/ Head head;
    private /*final*/ TileLayer[] tileLayers;
    private /*final*/ ArrayList <Entity> entities;

    /**
     * Constructs a PxPackMap object and parses a given PXPACK mapFile
     * to set the fields of the object
     *
     * @param inPath A File object pointing to a PXPACK map file
     *
     * @throws IOException if there was an error reading the PXPACK file
     * @throws ParseException if the mapFile format was somehow incorrect
     */
    public PxPack(final Path inPath) throws IOException, ParseException {
        mapPath = inPath;

        if (!inPath.toString().endsWith(".pxpack")) {
            throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.NOT_PXPACK"),
                                                                    inPath.toAbsolutePath()));
        }

        if (!Files.exists(inPath)) {
            System.err.println("ERROR: Could not locate PXPACK map file " + inPath.getFileName() + ".pxpack");
            return;
            //TODO: instead, initialize fields, exit
            //new mapFile will be made in save() method
        }

        try (SeekableByteChannel chan = Files.newByteChannel(inPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(Head.HEADER_STRING.length());
            chan.read(buf);

            if (!(new String(buf.array(), "SJIS").equals(Head.HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INCORRECT_HEADER"),
                                                              inPath.getFileName()),
                                         (int)chan.position());
            }

            final String description = readString(chan, 31, "description");

            final String[] mapNames = new String[4];
            for (int i = 0; i < mapNames.length; ++i) {
                mapNames[i] = readString(chan, 15, "map name");
            }

            final String spritesheetName = readString(chan, 15, "spritesheet name");

            buf = ByteBuffer.allocate(8);
            chan.read(buf);
            buf.flip();

            final byte[] data = Arrays.copyOf(buf.array(), 5);
            buf.position(buf.position() + 5);

            final int red = buf.get() & 0xFF;
            final int green = buf.get() & 0xFF;
            final int blue = buf.get() & 0xFF;
            final Color bgColor = Color.rgb(red, green, blue);

            final String[] tilesetNames = new String[NUM_LAYERS];
            final byte[] visibilityTypes = new byte[NUM_LAYERS];
            final byte[] scrollTypes = new byte[NUM_LAYERS];
            for (int i = 0; i < tilesetNames.length; ++i) {
                tilesetNames[i] = readString(chan, 15, "tileset name");

                if (tilesetNames[i].isEmpty()) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxPack.MISSING_TILESET"),
                                                                  i, inPath.getFileName()), (int)chan.position());
                }

                buf = ByteBuffer.allocate(2);
                chan.read(buf);
                buf.flip();

                visibilityTypes[i] = buf.get();
                scrollTypes[i] = buf.get();

                /*
                 * First byte is a sort of visibility toggle
                 *  - 0 -> invisible
                 *  - 2 -> visible
                 *  - 1 or >= 3 -> pulls wrong tiles from same tileset (offsets?)
                 *  - > 32 -> game crashes
                 *
                 *  Second byte is scroll type (scroll.txt)
                 */
            }

            head = new Head(description, mapNames, spritesheetName, data, bgColor, tilesetNames, visibilityTypes, scrollTypes);

            tileLayers = new TileLayer[NUM_LAYERS];

            for (int i = 0; i < tileLayers.length; ++i) {
                buf = ByteBuffer.allocate(8);
                chan.read(buf);
                if (!(new String(buf.array()).equals(TileLayer.HEADER_STRING))) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INCORRECT_LAYER_HEADER"),
                                                                  i, inPath.getFileName()), (int)chan.position());
                }

                buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                final int width = buf.getShort() & 0xFFFF;
                final int height = buf.getShort() & 0xFFFF;
                if (width * height > 0) {
                    chan.position(chan.position() + 1); //skip a byte (always 0)
                    //TODO: Find if it is ever not 0 (might've already checked but make sure)

                    buf = ByteBuffer.allocate(width * height);
                    chan.read(buf);
                    buf.flip();

                    final int[][] tiles = new int[height][width];
                    for (int y = 0; y < tiles.length; ++y) {
                        for (int x = 0; x < tiles[y].length; ++x) {
                            tiles[y][x] = buf.get() & 0xFF; //& 0xFF treats as unsigned byte when converted to int
                        }
                    }
                    tileLayers[i] = new TileLayer(tiles);
                }
                else {
                    tileLayers[i] = new TileLayer();
                }
            }

            buf = ByteBuffer.allocate(2);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            final int numEntities = buf.getShort();

            entities = new ArrayList <>(numEntities);

            for (int i = 0; i < numEntities; ++i) {
                buf = ByteBuffer.allocate(9);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                final byte flag = buf.get();

                final int type = buf.get() & 0xFF;

                final byte unknownByte = buf.get(); //index from tileset? subtype?

                final int x = buf.getShort() & 0xFFFF;
                final int y = buf.getShort() & 0xFFFF;

                final byte[] entityData = new byte[2];
                buf.get(entityData);

                final String name = readString(chan, 15, "entity name");

                entities.add(new Entity(flag, type, unknownByte, x, y, entityData, name));
            }
        }
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("PxPack.IOEXCEPT"), inPath.getFileName()), except);
        }
    }

    /**
     * Saves the PXPACK file
     */
    public void save() {
        try (SeekableByteChannel chan = Files.newByteChannel(mapPath, StandardOpenOption.WRITE,
                                                             StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.wrap(Head.HEADER_STRING.getBytes());
            chan.write(buf);

            writeString(head.getDescription(), chan);

            for (final String map : head.getMapNames()) {
                writeString(map, chan);
            }

            writeString(head.getSpritesheetName(), chan);

            buf = ByteBuffer.wrap(head.getData());
            chan.write(buf);

            final byte[] bgColor = {(byte)(head.getBgColor().getRed() * 255),
                                    (byte)(head.getBgColor().getGreen() * 255),
                                    (byte)(head.getBgColor().getBlue() * 255)};

            buf = ByteBuffer.wrap(bgColor);
            chan.write(buf);

            final String[] tilesetNames = head.getTilesetNames();
            final byte[] visibilityTypes = head.getVisibilityTypes();
            final byte[] scrollTypes = head.getScrollTypes();
            for (int i = 0; i < tilesetNames.length; ++i) {
                writeString(tilesetNames[i], chan);

                buf = ByteBuffer.allocate(2);
                buf.put(visibilityTypes[i]);
                buf.put(scrollTypes[i]);
                buf.flip();

                chan.write(buf);
            }

            for (final TileLayer layer : tileLayers) {
                buf = ByteBuffer.wrap(TileLayer.HEADER_STRING.getBytes());
                chan.write(buf);

                final int[][] layerData = layer.getTiles();
                if (null == layerData) {
                    buf = ByteBuffer.allocate(4);
                    buf.putShort((short)0).putShort((short)0);
                    buf.flip();

                    chan.write(buf);
                }
                else {
                    short width = (short)layerData[0].length;
                    short height = (short)layerData.length;

                    buf = ByteBuffer.allocate(5);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    buf.putShort(width).putShort(height);
                    buf.put((byte)0);
                    buf.flip();

                    chan.write(buf);

                    buf = ByteBuffer.allocate((width & 0xFFFF) * (height & 0xFFFF)); //& 0xFFFF treats as unsigned when converted to int
                    for (int[] row : layerData) {
                        for (int tile : row) {
                            buf.put((byte)tile);
                        }
                    }
                    buf.flip();
                    chan.write(buf);
                }
            }

            buf = ByteBuffer.allocate(2);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort((short)entities.size());
            buf.flip();

            chan.write(buf);

            for (final Entity e : entities) {
                buf = ByteBuffer.allocate(9);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                buf.put(e.getFlag());

                buf.put((byte)e.getType());

                buf.put(e.getUnknownByte());

                buf.putShort((short)e.getX());
                buf.putShort((short)e.getY());

                buf.put(e.getUnknownData());

                buf.flip();
                chan.write(buf);

                writeString(e.getName(), chan);
            }
        }
        catch (final FileNotFoundException except) {
            //TODO: create the file
        }
        catch (final IOException except) {
            except.printStackTrace();
        }
    }

    public String getName() {
        final String mapFname = mapPath.getFileName().toString();
        return mapFname.substring(0, mapFname.lastIndexOf(".pxpack"));
    }

    //TODO: Make this undo/redo friendly?
    public void rename(final String newName) throws IOException {
        mapPath = Files.move(mapPath, mapPath.resolveSibling(newName + ".pxpack"));
    }

    public Head getHead() {
        return head;
    }

    public TileLayer[] getTileLayers() {
        return Arrays.copyOf(tileLayers, tileLayers.length); //shallow copy of elements
        //element references are same but array reference is diff
    }

    public ArrayList <Entity> getEntities() {
        //TODO: disallow null elements
        //(maybe have this return deep copy unmodifiable list and provide set/add methods)
        //also cap length to 0xFFFF
        return entities;
    }

    /**
     * Reads a string from a PXPACK file tied to a given FileChannel
     *
     * @param chan The {@code SeekableByteChannel} object to read from
     * @param maxLen The maximum length for the string being read
     * @param type A {@code String} denoting the type of the string being read (what it is for)
     *
     * @return The String that was read
     *
     * @throws IOException if there was an error reading the string from the PXPACK file
     */
    private String readString(final SeekableByteChannel chan, final int maxLen, final String type)
            throws IOException, ParseException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        chan.read(buf);
        buf.flip();

        int strLen = buf.get() & 0xFF;
        if (maxLen < strLen) {
            throw new ParseException(MessageFormat.format(Messages.getString("PxPack.ReadString.TOO_LONG"),
                                                          type, maxLen, strLen), (int)chan.position());
        }

        buf = ByteBuffer.allocate(strLen);
        chan.read(buf);

        return new String(buf.array(), "SJIS");
    }

    private void writeString(final String str, final SeekableByteChannel chan) throws IOException {
        final byte[] strAsBytes = str.getBytes("SJIS");
        final ByteBuffer buf = ByteBuffer.allocate(1 + strAsBytes.length);

        buf.put((byte)strAsBytes.length);
        buf.put(strAsBytes);
        buf.flip();

        chan.write(buf);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(MessageFormat.format(Messages.getString("PxPack.ToString.NAME"), mapPath.getFileName()));

        result.append(head);
        result.append('\n');

        for (TileLayer layer : tileLayers) {
            result.append(layer);
            result.append('\n');
        }
        for (Entity entity : entities) {
            result.append(entity);
        }

        return result.toString();
    }

    public static final class Head {
        private static final String HEADER_STRING = "PXPACK121127a**\0";

        private String description;
        private final String[] mapNames;
        private String spritesheetName;
        private final byte[] data; //TODO: Make int[]?
        private Color bgColor;
        private final String[] tilesetNames;
        private final byte[] visibilityTypes;
        private final byte[] scrollTypes;

        //TODO: add reset()?

        Head() {
            description = "";
            mapNames = new String[4];
            spritesheetName = "";
            data = new byte[5];
            bgColor = Color.BLACK;
            tilesetNames = new String[NUM_LAYERS];
            visibilityTypes = new byte[NUM_LAYERS];
            scrollTypes = new byte[NUM_LAYERS];
        }

        Head(final Head head) {
            this.description = head.description;
            this.mapNames = head.getMapNames();
            this.spritesheetName = head.spritesheetName;
            this.data = head.getData();
            this.bgColor = head.bgColor;
            this.tilesetNames = head.getTilesetNames();
            this.visibilityTypes = head.getVisibilityTypes();
            this.scrollTypes = head.getScrollTypes();
        }

        Head(final String description, final String[] mapNames, final String spritesheetName,
             final byte[] data, final Color bgColor, final String[] tilesetNames,
             final byte[] visibilityTypes, final byte[] scrollTypes) {

            //TODO: check if these method calls are bad practice
            setDescription(description);

            this.mapNames = new String[4];
            for (int i = 0; i < this.mapNames.length; ++i) {
                setMapName(i, mapNames[i]);
            }

            setSpritesheetName(spritesheetName);

            setBgColor(bgColor);

            this.data = new byte[5];
            for (int i = 0; i < this.data.length; ++i) {
                setData(i, data[i]);
            }

            this.tilesetNames = new String[NUM_LAYERS];
            for (int i = 0; i < this.tilesetNames.length; ++i) {
                setTilesetName(i, tilesetNames[i]);
            }

            this.visibilityTypes = new byte[NUM_LAYERS];
            for (int i = 0; i < this.visibilityTypes.length; ++i) {
                setVisibilityType(i, visibilityTypes[i]);
            }

            this.scrollTypes = new byte[NUM_LAYERS];
            for (int i = 0; i < this.scrollTypes.length; ++i) {
                setScrollType(i, scrollTypes[i]);
            }
        }

        public String getDescription() {
            return description;
        }

        public String[] getMapNames() {
            return Arrays.copyOf(mapNames, mapNames.length);
        }

        public String getSpritesheetName() {
            return spritesheetName;
        }

        public byte[] getData() {
            return Arrays.copyOf(data, data.length);
        }

        public Color getBgColor() {
            return bgColor;
        }

        public String[] getTilesetNames() {
            return Arrays.copyOf(tilesetNames, tilesetNames.length);
        }

        public byte[] getVisibilityTypes() {
            return Arrays.copyOf(visibilityTypes, visibilityTypes.length);
        }

        public byte[] getScrollTypes() {
            return Arrays.copyOf(scrollTypes, scrollTypes.length);
        }

        public void setDescription(final String description) {
            if (description.length() > 31) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.Setter.ERROR_MSG"),
                                                                        "description text", 31));
            }
            this.description = description;
        }

        public void setMapName(final int index, final String mapName) {
            if (mapName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.NAME_SETTER_ERROR_MSG"),
                                                                        "mapname", 15));
            }
            this.mapNames[index] = mapName;
        }

        public void setSpritesheetName(final String spritesheetName) {
            if (spritesheetName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.NAME_SETTER_ERROR_MSG"),
                                                                        "spritesheet name", 15));
            }
            this.spritesheetName = spritesheetName;
        }

        public void setData(final int index, final byte unknownByte) {
            //TODO: validate (how?)
            data[index] = unknownByte;
        }

        public void setBgColor(final Color color) {
            //TODO: verify kero blaster doesn't support bg opacity
            if (!color.isOpaque()) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Head.COLOR_SETTER_ERROR_MSG"));
            }
            bgColor = color;
        }

        public void setTilesetName(final int index, String tilesetName) {
            if (tilesetName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.NAME_SETTER_ERROR_MSG"),
                                                                        "tileset name", 15));
            }
            this.tilesetNames[index] = tilesetName;
        }

        public void setVisibilityType(final int index, final byte visibility) {
            if ((visibility & 0xFF) > 32) {
                throw new IllegalArgumentException("Visibility type must be <= 32"); //TODO: Create messages.properties string
            }
            visibilityTypes[index] = visibility;
        }

        public void setScrollType(final int index, final byte scroll) {
            if ((scroll & 0xFF) > 9) {
                throw new IllegalArgumentException("Scroll type must be <= 9"); //TODO: Create messages.properties string
            }
            scrollTypes[index] = scroll;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.DESCRIPTION"), description));

            for (int i = 0; i < mapNames.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.MAPNAME"), i, mapNames[i]));
            }

            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.SPRITESHEET_NAME"), spritesheetName));

            for (int i = 0; i < data.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.UNKNOWN_BYTES"), i,
                                                   String.format("%02X", data[i])));
            }

            //TODO: Format as hex
            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.BACKGROUND_COLOR"),
                                               bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()));

            for (int i = 0; i < tilesetNames.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.TILESET_NAME"), i,
                                                   tilesetNames[i]));
            }

            return result.toString();
        }
    }

    public static final class TileLayer {
        private static final String HEADER_STRING = "pxMAP01\0";

        private int[][] tiles;

        TileLayer() {

        }

        TileLayer(final TileLayer layer) {
            tiles = layer.getTiles();
        }

        TileLayer(final int[][] tiles) {
            if (null != tiles) {
                if (tiles.length > 0xFFFF || tiles[0].length > 0xFFFF) {
                    throw new IllegalArgumentException(Messages.getString("PxPack.TileLayer.ARR_CONSTRUCTOR_ERROR_MSG"));
                }

                this.tiles = new int[tiles.length][tiles[0].length];
                for (int y = 0; y < tiles.length; ++y) {
                    System.arraycopy(tiles[y], 0, this.tiles[y], 0, tiles[y].length);
                }
            }
        }

        /**
         * Resize the tile layer's dimensions
         *
         * @param width The new width for the layer
         * @param height The new height for the layer
         */
        public void resize(final int width, final int height) {
            if (width > 0xFFFF || height > 0xFFFF) {
                throw new IllegalArgumentException(Messages.getString("PxPack.TileLayer.RESIZE_ERROR_MSG"));
            }

            if (null == tiles) {
                tiles = new int[height][width];
                return;
            }

            if (width == tiles[0].length && height == tiles.length) {
                return;
            }

            if (0 == width || 0 == height) {
                tiles = null;
                return;
            }

            final int[][] oldTiles = new int[tiles.length][tiles[0].length];
            for (int y = 0; y < tiles.length; ++y) {
                System.arraycopy(tiles[y], 0, oldTiles[y], 0, tiles[y].length);
            }

            tiles = new int[height][width];

            //loop for each index in smaller dimension
            for (int y = 0; y < (height < oldTiles.length ? height : oldTiles.length); ++y) {
                System.arraycopy(oldTiles[y], 0, tiles[y], 0, (width < oldTiles[y].length ? width : oldTiles[y].length));
            }
        }

        public int[][] getTiles() {
            if (null != tiles) {
                final int[][] tilesCopy = new int[tiles.length][tiles[0].length];
                for (int y = 0; y < tiles.length; ++y) {
                    System.arraycopy(tiles[y], 0, tilesCopy[y], 0, tiles[y].length);
                }
                return tilesCopy;
            }
            return null;
        }

        public void setTile(final int x, final int y, final int tile) {
            if (tile < 0 || tile > 0xFF) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.TileLayer.SET_TILE_ERROR_MSG"),
                                                                        x, y));
            }
            tiles[y][x] = tile;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            if (null == tiles) {
                result.append(MessageFormat.format(Messages.getString("PxPack.TileLayer.ToString.WIDTH"), "00"));
                result.append(MessageFormat.format(Messages.getString("PxPack.TileLayer.ToString.HEIGHT"), "00"));
            }
            else {
                result.append(MessageFormat.format(Messages.getString("PxPack.TileLayer.ToString.WIDTH"),
                                                   String.format("%02X", tiles[0].length)));
                result.append(MessageFormat.format(Messages.getString("PxPack.TileLayer.ToString.HEIGHT"),
                                                   String.format("%02X", tiles.length)));
                for (final int[] row : tiles) {
                    result.append('\t');
                    for (final int tile : row) {
                        result.append(String.format("%02X", tile));
                        result.append(' ');
                    }
                    result.append('\n');
                }
            }
            return result.toString();
        }
    }

    public static final class Entity {
        private int type;
        private byte flag, unknownByte; //TODO: make ints?
        private int x, y;
        private byte[] unknownData;
        private String name;

        Entity() {
            unknownData = new byte[2];
            name = "";
        }

        Entity(Entity entity) {
            this.flag = entity.flag;
            this.x = entity.x;
            this.y = entity.y;
            this.unknownData = entity.unknownData;
            this.name = entity.name;
        }

        Entity(final byte flag, final int type, final byte unknownByte, final int x, final int y,
               final byte[] unknownData, final String name) {

            //TODO: call x and y setters instead of this?
            if (x > 0xFFFF || y > 0xFFFF) {
                throw new IllegalArgumentException("Attempt to create entity with x or y coordinate exceeding 0xFFFF");
            }
            this.flag = flag;
            this.type = type;
            this.unknownByte = unknownByte;
            this.x = x;
            this.y = y;
            this.unknownData = Arrays.copyOf(unknownData, 2);
            this.name = name;
        }

        public byte getFlag() {
            return flag;
        }

        public int getType() {
            return type;
        }

        public byte getUnknownByte() {
            return unknownByte;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public byte[] getUnknownData() {
            return Arrays.copyOf(unknownData, unknownData.length);
        }

        public String getName() {
            return name;
        }

        public void setFlag(final byte flag) {
            this.flag = flag;
        }

        public void setType(final int type) {
            this.type = type;
        }

        public void setUnknownByte(final byte unknownByte) {
            this.unknownByte = unknownByte;
        }

        public void setX(final int x) {
            if (x < 0 || x > 0xFFFF) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Entity.COORDINATE_SETTER_ERROR_MSG"),
                                                                        "x"));
            }
            this.x = x;
        }

        public void setY(final int y) {
            if (x < 0 || y > 0xFFFF) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Entity.COORDINATE_SETTER_ERROR_MSG"),
                                                                        "y"));
            }
            this.y = y;
        }

        public void setUnknownData(final int index, final byte data) {
            this.unknownData[index] = data;
        }

        public void setName(final String name) {
            if (name.length() > 15) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Entity.NAME_SETTER_ERROR_MSG"));
            }
            this.name = name;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.FLAG"),
                                               String.format("%02X", flag)));
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.TYPE"),
                                               String.format("%02X", type)));
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.UNKNOWN_BYTE"),
                                               String.format("%02X", unknownByte)));
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.X_COORDINATE"),
                                               String.format("%02X", x)));
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.Y_COORDINATE"),
                                               String.format("%02X", y)));
            for (int i = 0; i < unknownData.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.DATA"),
                                                   i, String.format("%02X", unknownData[i])));
            }
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.NAME"), name));
            result.append('\n');

            return result.toString();
        }
    }
}