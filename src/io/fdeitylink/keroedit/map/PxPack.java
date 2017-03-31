/*
 * TODO:
 * Allow direct modification of a TileLayer? (in relation to PxPack object)
 * Default initialize strings as empty? (files known to pre-exist for filenames?)
 * Store all unknown bytes in header of file
 * Make rename() undoable
 * When entityNum is changed, resort list
 *  - same for tilesets and tile layers
 *  - Do I even need entity & layer to store numbers?
 * Change use of short to int for entity type?
 * Check extension
 * Read in scroll types (second byte after tileset name)
 * Copy constructor and new map constructor
 * Throw except for missing tileset names
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
public class PxPack {
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

        //TODO: check if file doesn't exist
        if (!Files.exists(inPath)) {
            System.err.println("ERROR: Could not locate PXPACK map file " + inPath.getFileName() + ".pxpack");
            //TODO: initialize fields, exit; new mapFile will be made in save() method
        }

        try (SeekableByteChannel chan = Files.newByteChannel(inPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(Head.HEADER_STRING.length());
            chan.read(buf);

            if (!(new String(buf.array()).equals(Head.HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INCORRECT_HEADER"),
                                                              inPath.getFileName()),
                                         (int)chan.position());
            }

            final String description = readString(chan);

            final String[] mapNames = new String[4];
            for (int i = 0; i < mapNames.length; ++i) {
                mapNames[i] = readString(chan);
            }

            final String spritesheetName = readString(chan);

            buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            final byte[] unknownBytes = new byte[5];
            for (int i = 0; i < unknownBytes.length; ++i) {
                unknownBytes[i] = buf.get();
            }

            final int red = buf.get() & 0xFF;
            final int green = buf.get() & 0xFF;
            final int blue = buf.get() & 0xFF;
            final Color bgColor = Color.rgb(red, green, blue);

            final String[] tilesetNames = new String[NUM_LAYERS];
            for (int i = 0; i < tilesetNames.length; ++i) {
                tilesetNames[i] = readString(chan);
                chan.position(chan.position() + 2); //skip 2 bytes after each tileset name
                //First byte is a sort of visibility toggle
                //0 = invisible; 2 = visible;
                //1 || >= 3 = pulls wrong tiles but from same tileset (offset?);
                //> 32 = game crashes
                //Second is scroll type (scroll.txt)
            }

            head = new Head(description, mapNames, spritesheetName, unknownBytes, bgColor, tilesetNames);

            tileLayers = new TileLayer[NUM_LAYERS];

            for (int i = 0; i < tileLayers.length; ++i) {
                buf = ByteBuffer.allocate(8);
                chan.read(buf);
                if (!(new String(buf.array()).equals(TileLayer.HEADER_STRING))) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INCORRECT_LAYER_HEADER"),
                                                                  i, inPath.getFileName()),
                                             (int)chan.position());
                }

                buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                final int width = buf.getShort() & 0xFFFF;
                final int height = buf.getShort() & 0xFFFF;
                if (width * height > 0) {
                    chan.position(chan.position() + 1);

                    buf = ByteBuffer.allocate(width * height);
                    chan.read(buf);
                    buf.flip();

                    final int[][] tiles = new int[height][width];
                    for (int y = 0; y < tiles.length; ++y) {
                        for (int x = 0; x < tiles[y].length; ++x) {
                            tiles[y][x] = buf.get() & 0xFF; //& 0xFF treats it as unsigned byte when converted to int
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
                final byte[] data = new byte[2];
                buf.get(data);
                final String name = readString(chan);

                entities.add(new Entity(flag, type, unknownByte, x, y, data, name));
            }
        }
        /*catch (final FileNotFoundException except) {
            System.err.println("ERROR: Could not locate PXPACK map file " + inPath.getFileName() + ".pxpack");
            //TODO: initialize fields, exit; new mapFile will be made in save() method
        }*/
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("PxPack.IOEXCEPT"), inPath.getFileName()), except);
        }

        //save();
    }

    /**
     * Saves the PXPACK file
     */
    public void save() {
        try (SeekableByteChannel chan = Files.newByteChannel(mapPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            Files.copy(mapPath, java.nio.file.Paths.get(mapPath.toAbsolutePath().toString().replace(".pxpack", "") +
                                                        Math.random() + ".pxpack"));

            ByteBuffer buf = ByteBuffer.wrap(Head.HEADER_STRING.getBytes());
            chan.write(buf);

            writeString(head.getDescription(), chan);

            for (final String map : head.getMapNames()) {
                writeString(map, chan);
            }

            writeString(head.getSpritesheetName(), chan);

            buf = ByteBuffer.wrap(head.getUnknownBytes());
            chan.write(buf);

            final byte[] bgColor = {(byte)(head.getBgColor().getRed() * 255),
                                    (byte)(head.getBgColor().getGreen() * 255),
                                    (byte)(head.getBgColor().getBlue() * 255)};

            buf = ByteBuffer.wrap(bgColor);
            chan.write(buf);

            for (final String tilesetName : head.getTilesetNames()) {
                writeString(tilesetName, chan);
                buf = ByteBuffer.wrap(new byte[]{0, 0});
                chan.write(buf);
            }

            /*buf = ByteBuffer.wrap(TileLayer.HEADER_STRING.getBytes());
            chan.write(buf);*/
        }
        catch (final FileNotFoundException except) {

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
        //final File renamedFile = new File(mapPath.getParent() + File.separatorChar + newName + ".pxpack");
        /*final Path renamedPath = Paths.get(mapPath.getParent().toAbsolutePath().toString() + File.separatorChar +
                                          newName + ".pxpack");*/

        /*if (Files.exists(renamedPath)) {
            throw new IOException("Attempt to rename " + mapPath.getName() + ".pxpack to " +
                                  renamedPath.getFileName() + ".pxpack" +
                                  " failed because that PXPACK file already exists!");
        }

        if (!mapPath.renameTo(renamedPath)) {
            throw new IOException("Attempt to rename " + mapPath.getFileName() + ".pxpack" + " to "
                                  + renamedPath.getFileName() + ".pxpack" + " failed for an unknown reason");
        }*/

        //mapFile.delete();
        //mapPath = renamedPath;
    }

    public Head getHead() {
        return head;
    }

    public TileLayer[] getTileLayers() {
        return tileLayers;
    }

    public ArrayList <Entity> getEntities() {
        /*final ArrayList <Entity> entities = new ArrayList <>(this.entities.size());
        for (final Entity e : this.entities) {
            entities.add(new Entity(e));
        }*/
        return entities;
    }

    //TODO: throw NullPointerExceptions for null values

    /**
     * Reads a string from a PXPACK file tied to a given FileChannel
     *
     * @param chan The SeekableByteChannel object to read from
     *
     * @return The String that was read
     *
     * @throws IOException if there was an error reading the string from the PXPACK file
     */
    private String readString(final SeekableByteChannel chan) throws IOException {
        String result = "";
        try {
            ByteBuffer buf = ByteBuffer.allocate(1);
            chan.read(buf);
            buf.flip();

            byte strLen = buf.get();

            buf = ByteBuffer.allocate(strLen);
            chan.read(buf);

            result = new String(buf.array(), "SJIS");
        }
        catch (final UnsupportedEncodingException except) {
            //TODO: throw error/show message?
            System.err.println(Messages.getString("UNSUPPORTED_ENCODING"));
        }
        return result;
    }

    private void writeString(final String str, final SeekableByteChannel chan) throws IOException {
        //seems to not work, IDK why
        final byte[] strAsBytes = str.getBytes("SJIS");
        final ByteBuffer buf = ByteBuffer.allocate(1 + strAsBytes.length);

        buf.put((byte)strAsBytes.length);
        buf.put(strAsBytes);
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

    public class Head {
        private static final String HEADER_STRING = "PXPACK121127a**\0";

        private final String[] mapNames;
        private final String[] tilesetNames;
        private String description;
        private String spritesheetName;
        private final byte[] unknownBytes; //TODO: Make int[]?
        private Color bgColor;

        //TODO: add reset()?

        Head() {
            mapNames = new String[4];
            tilesetNames = new String[NUM_LAYERS];
            description = "";
            spritesheetName = "";
            unknownBytes = new byte[5];
            bgColor = Color.BLACK;
        }

        Head(final Head head) {
            this.description = head.description;
            this.mapNames = Arrays.copyOf(head.mapNames, head.mapNames.length);
            this.spritesheetName = head.spritesheetName;
            this.unknownBytes = Arrays.copyOf(head.unknownBytes, head.unknownBytes.length);
            this.bgColor = head.bgColor;
            this.tilesetNames = Arrays.copyOf(head.tilesetNames, head.tilesetNames.length);
        }

        Head(final String description, final String[] mapNames, final String spritesheetName,
             final byte[] unknownBytes, final Color bgColor, final String[] tilesetNames) {
            //TODO: check if these method calls are bad practice
            setDescription(description);

            //TODO: Use Arrays.copyOf instead of System.arraycopy?

            this.mapNames = new String[4];
            System.arraycopy(mapNames, 0, this.mapNames, 0, this.mapNames.length);

            setSpritesheetName(spritesheetName);
            this.bgColor = bgColor;

            this.unknownBytes = new byte[5];
            System.arraycopy(unknownBytes, 0, this.unknownBytes, 0, this.unknownBytes.length);

            this.tilesetNames = new String[NUM_LAYERS];
            System.arraycopy(tilesetNames, 0, this.tilesetNames, 0, this.tilesetNames.length);
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

        public byte[] getUnknownBytes() {
            return Arrays.copyOf(unknownBytes, unknownBytes.length);
        }

        public Color getBgColor() {
            return bgColor;
        }

        public String[] getTilesetNames() {
            return Arrays.copyOf(tilesetNames, tilesetNames.length);
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

        public void setUnknownByte(final int index, final byte unknownByte) {
            unknownBytes[index] = unknownByte;
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

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.DESCRIPTION"), description));

            for (int i = 0; i < mapNames.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.MAPNAME"), i, mapNames[i]));
            }

            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.SPRITESHEET_NAME"), spritesheetName));

            for (int i = 0; i < unknownBytes.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.UNKNOWN_BYTES"), i,
                                                   String.format("%02X", unknownBytes[i])));
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

    public class TileLayer {
        private static final String HEADER_STRING = "pxMAP01\0";

        private int[][] tiles;

        TileLayer() {

        }

        TileLayer(final TileLayer layer) {
            if (null != layer.tiles) {
                tiles = new int[layer.tiles.length][layer.tiles[0].length];
                for (int y = 0; y < layer.tiles.length; ++y) {
                    System.arraycopy(layer.tiles[y], 0, tiles[y], 0, layer.tiles[y].length);
                }
            }
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

    public class Entity {
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