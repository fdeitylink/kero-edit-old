/*
 * TODO:
 * Default initialize strings as empty? (files known to pre-exist for filenames?)
 * Make rename() undoable?
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

import javafx.scene.paint.Color;

import io.fdeitylink.keroedit.Messages;

/**
 * Object for storing information about a PXPACK map file
 */
public final class PxPack {
    public static final int NUM_LAYERS = 3;

    private Path mapPath;

    private final Head head;
    private final TileLayer[] tileLayers;
    private final ArrayList <Entity> entities;

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
            //TODO: set inPath to an internal default pxpack file and parse that?
            //clone internal file to outside?
            head = new Head("", new String[]{"", "", "", ""}, "", new byte[]{0, 0, 0, 0, 0}, Color.BLACK,
                            new String[]{"mpt00", "", ""}, new byte[]{2, 2, 2}, new byte[]{0, 0, 1});
            //All images named must exist
            //Only first tileset is required
            //Use most common values for defaults

            tileLayers = new TileLayer[NUM_LAYERS];
            for (int i = 0; i < tileLayers.length; ++i) {
                tileLayers[i] = new TileLayer();
            }

            entities = new ArrayList <>();
            return;
        }

        try (SeekableByteChannel chan = Files.newByteChannel(inPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(Head.HEADER_STRING.length());
            chan.read(buf);

            if (!(new String(buf.array(), "SJIS").equals(Head.HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INVALID_HEADER"),
                                                              inPath.getFileName()), (int)chan.position());
            }

            final String description = readString(chan, 31, "description");

            final String[] mapNames = new String[Head.NUM_REF_MAPS];
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

                if (0 == i && tilesetNames[i].isEmpty()) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxPack.MISSING_FIRST_TILESET"),
                                                                  inPath.getFileName()), (int)chan.position());
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
                buf = ByteBuffer.allocate(TileLayer.HEADER_STRING.length());
                chan.read(buf);
                if (!(new String(buf.array()).equals(TileLayer.HEADER_STRING))) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INVALID_LAYER_HEADER"),
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
                            tiles[y][x] = buf.get() & 0xFF;
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
     * Saves the PXPACK file represented by this object
     */
    public void save() {
        try (SeekableByteChannel chan = Files.newByteChannel(mapPath,
                                                             StandardOpenOption.WRITE,
                                                             StandardOpenOption.TRUNCATE_EXISTING,
                                                             StandardOpenOption.CREATE)) {
            ByteBuffer buf = ByteBuffer.wrap(Head.HEADER_STRING.getBytes());
            chan.write(buf);

            writeString(head.getDescription(), chan);

            for (final String map : head.getMapNames()) {
                writeString(map, chan);
            }

            writeString(head.getSpritesheetName(), chan);

            buf = ByteBuffer.wrap(head.getData());
            chan.write(buf);

            buf = ByteBuffer.wrap(new byte[]{(byte)(head.getBgColor().getRed() * 255),
                                             (byte)(head.getBgColor().getGreen() * 255),
                                             (byte)(head.getBgColor().getBlue() * 255)});
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

                buf.put(e.getData());

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

    public void rename(final String newName) throws IOException {
        mapPath = Files.move(mapPath, mapPath.resolveSibling(newName + ".pxpack"));
    }

    public Head getHead() {
        return head;
    }

    public TileLayer[] getTileLayers() {
        return Arrays.copyOf(tileLayers, tileLayers.length); //just shallow copies elements
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
     * @param type A {@code String} denoting the type of the string being read (what it is for).
     * Used to provide a descriptive exception message if the read string is of invalid length
     * or if it contains spaces.
     *
     * @return The String that was read
     *
     * @throws IOException if there was an error reading the string from the PXPACK file
     * @throws ParseException if the string was too long (as per {@code maxLen} or contained spaces
     */
    private String readString(final SeekableByteChannel chan, final int maxLen, final String type)
            throws IOException, ParseException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        chan.read(buf);
        buf.flip();

        final int strLen = buf.get() & 0xFF;
        if (maxLen < strLen) {
            throw new ParseException(MessageFormat.format(Messages.getString("PxPack.ReadString.INVALID_LEN"),
                                                          type, maxLen, strLen), (int)chan.position());
        }

        buf = ByteBuffer.allocate(strLen);
        chan.read(buf);

        final String ret = new String(buf.array(), "SJIS");
        if (!"description".equals(type) && ret.contains(" ")) {
            throw new ParseException(MessageFormat.format(Messages.getString("PxPack.ReadString.CONTAINS_SPACE"),
                                                          type), (int)chan.position());
        }

        return ret;
    }

    private void writeString(final String str, final SeekableByteChannel chan) throws IOException {
        //TODO: test with 0 len str
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
        //TODO: Subclass to store tileset name, visibility, and scroll?
        static final String HEADER_STRING = "PXPACK121127a**\0";
        static final int NUM_REF_MAPS = 4;

        private String description;
        private final String[] mapNames;
        private String spritesheetName;

        private final byte[] data;
        private Color bgColor;

        private final String[] tilesetNames;
        private final byte[] visibilityTypes;
        private final byte[] scrollTypes;

        Head(final String description, final String[] mapNames, final String spritesheetName,
             final byte[] data, final Color bgColor, final String[] tilesetNames,
             final byte[] visibilityTypes, final byte[] scrollTypes) {
            if (NUM_REF_MAPS != mapNames.length) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_ARR_LEN"),
                                                                        "mapNames", mapNames.length, NUM_REF_MAPS));
            }
            if (5 != data.length) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_ARR_LEN"),
                                                                        "data", data.length, 5));
            }
            if (NUM_LAYERS != tilesetNames.length) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_ARR_LEN"),
                                                                        "tilesetNames", tilesetNames.length, NUM_LAYERS));
            }
            if (NUM_LAYERS != visibilityTypes.length) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_ARR_LEN"),
                                                                        "visibilityTypes", visibilityTypes.length, NUM_LAYERS));
            }
            if (NUM_LAYERS != scrollTypes.length) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_ARR_LEN"),
                                                                        "scrollTypes", scrollTypes.length, NUM_LAYERS));
            }

            //TODO: check if these method calls are bad practice
            setDescription(description);

            this.mapNames = new String[NUM_REF_MAPS];
            for (int i = 0; i < this.mapNames.length; ++i) {
                setMapName(i, mapNames[i]);
            }

            setSpritesheetName(spritesheetName);

            this.data = new byte[5];
            for (int i = 0; i < this.data.length; ++i) {
                setData(i, data[i]);
            }

            setBgColor(bgColor);

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
            if (null == description) {
                throw new NullPointerException(MessageFormat.format(Messages.getString("PxPack.NULL_ARG"), "description"));
            }
            if (description.length() > 31) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_STR_LEN"),
                                                                        "description text", 31));
            }
            this.description = description;
        }

        public void setMapName(final int index, final String mapName) {
            if (null == mapName) {
                throw new NullPointerException(MessageFormat.format(Messages.getString("PxPack.NULL_ARG"), "mapName"));
            }
            if (mapName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_STR_LEN"),
                                                                        "mapName", mapName.length(), 15));
            }
            if (mapName.contains(" ")) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.STR_CONTAINS_SPACE"),
                                                                        "mapName"));
            }
            this.mapNames[index] = mapName;
        }

        public void setSpritesheetName(final String spritesheetName) {
            if (null == spritesheetName) {
                throw new NullPointerException(MessageFormat.format(Messages.getString("PxPack.NULL_ARG"), "spritesheetName"));
            }
            if (spritesheetName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_STR_LEN"),
                                                                        "spritesheetName", spritesheetName.length(), 15));
            }
            if (spritesheetName.contains(" ")) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.STR_CONTAINS_SPACE"),
                                                                        "spritesheetName"));
            }
            this.spritesheetName = spritesheetName;
        }

        public void setData(final int index, final byte unknownByte) {
            //TODO: validate (how?)
            data[index] = unknownByte;
        }

        public void setBgColor(final Color color) {
            if (null == color) {
                throw new NullPointerException(MessageFormat.format(Messages.getString("PxPack.NULL_ARG"), "color"));
            }
            //TODO: verify kero blaster doesn't support bg opacity
            if (!color.isOpaque()) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Head.COLOR_NOT_OPAQUE"));
            }
            bgColor = color;
        }

        public void setTilesetName(final int index, final String tilesetName) {
            if (null == tilesetName) {
                throw new NullPointerException(MessageFormat.format(Messages.getString("PxPack.NULL_ARG"), "tilesetName"));
            }
            //only first is required
            if (0 == index && tilesetName.isEmpty()) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Head.EMPTY_FIRST_TILESET_NAME"));
            }
            if (tilesetName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_STR_LEN"),
                                                                        "tilesetName", tilesetName.length(), 15));
            }
            if (tilesetName.contains(" ")) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.STR_CONTAINS_SPACE"),
                                                                        "tilesetName"));
            }
            this.tilesetNames[index] = tilesetName;
        }

        public void setVisibilityType(final int index, final byte visibility) {
            if ((visibility & 0xFF) > 32) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Head.INVALID_VISIBILITY"));
            }
            visibilityTypes[index] = visibility;
        }

        public void setScrollType(final int index, final byte scroll) {
            if ((scroll & 0xFF) > 9) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Head.INVALID_SCROLL"));
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
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.UNKNOWN_BYTES"),
                                                   i, String.format("%02X", data[i])));
            }

            //TODO: Format as hex
            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.BACKGROUND_COLOR"),
                                               bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()));

            for (int i = 0; i < tilesetNames.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.TILESET_NAME"),
                                                   i, tilesetNames[i]));
            }

            return result.toString();
        }
    }

    public static final class TileLayer {
        static final String HEADER_STRING = "pxMAP01\0";

        private int[][] tiles;

        TileLayer() {

        }

        TileLayer(final int[][] tiles) {
            if (null != tiles && 0 < tiles.length) {
                if (tiles.length > 0xFFFF || tiles[0].length > 0xFFFF) {
                    throw new IllegalArgumentException(Messages.getString("PxPack.TileLayer.TILES_TOO_LARGE"));
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

            //loop & copy for each index in smaller dimension
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
        private byte flag; //TODO: make int?
        private byte unknownByte; //make int?
        private int x, y;
        private final byte[] data;
        private String name;

        Entity(final byte flag, final int type, final byte unknownByte, final int x, final int y,
               final byte[] data, final String name) {
            if (data.length != 2) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_ARR_LEN"),
                                                                        "data", data.length, 2));
            }

            setFlag(flag);
            setType(type);
            setUnknownByte(unknownByte);
            setCoordinates(x, y);

            this.data = new byte[2];
            for (int i = 0; i < this.data.length; ++i) {
                setData(i, data[i]);
            }
            setName(name);
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

        public byte[] getData() {
            return Arrays.copyOf(data, data.length);
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
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Entity.INVALID_COORD"),
                                                                        "x"));
            }
            this.x = x;
        }

        public void setY(final int y) {
            if (x < 0 || y > 0xFFFF) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Entity.INVALID_COORD"),
                                                                        "y"));
            }
            this.y = y;
        }

        public void setCoordinates(final int x, final int y) {
            setX(x);
            setY(y);
        }

        public void setData(final int index, final byte data) {
            this.data[index] = data;
        }

        public void setName(final String entityName) {
            if (null == entityName) {
                throw new NullPointerException(MessageFormat.format(Messages.getString("PxPack.NULL_ARG"), "entityName"));
            }
            if (entityName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.INVALID_STR_LEN"),
                                                                        "entityName", entityName.length(), 15));
            }
            if (entityName.contains(" ")) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.STR_CONTAINS_SPACE"),
                                                                        "entityName"));
            }
            this.name = entityName;
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
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.X"),
                                               String.format("%02X", x)));
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.Y"),
                                               String.format("%02X", y)));
            for (int i = 0; i < data.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.DATA"),
                                                   i, String.format("%02X", data[i])));
            }
            result.append(MessageFormat.format(Messages.getString("PxPack.Entity.ToString.NAME"), name));
            result.append('\n');

            return result.toString();
        }
    }
}