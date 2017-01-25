/*
 * TODO:
 * Make this (or at least the interior classes) immutable?
 * Allow direct modification of a TileLayer? (in relation to PxPack object)
 * Default initialize strings as empty? (files known to pre-exist for filenames?)
 * Store all unknown bytes in header of file
 * Make rename() undoable
 * When entityNum is changed, resort list
 *  - same for tilesets and tile layers
 *  - Do I even need entity & layer to store numbers?
 * Change use of short to int for entity type?
 */
package com.fdl.keroedit.map;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.text.ParseException;

import java.io.UnsupportedEncodingException;

import javafx.scene.paint.Color;

import com.fdl.keroedit.util.Logger;

import com.fdl.keroedit.Messages;

/**
 * Object for storing information about a PXPACK file
 */
public class PxPack {
    private File file;
    private /*final*/ Head head;
    private /*final*/ TileLayer[] tileLayers;
    private /*final*/ ArrayList <Entity> entities;

    /**
     * Constructs a PxPackMap object and parses a given PXPACK file
     * to set the fields of the object
     *
     * @param inFile A File object pointing to a PXPACK file
     *
     * @throws IOException    if there was an error reading the file
     * @throws ParseException if the file format was somehow incorrect
     */
    public PxPack(final File inFile) throws IOException, ParseException {
        file = inFile;

        FileInputStream inStream = null;
        FileChannel chan = null;

        try {
            inStream = new FileInputStream(inFile);
            chan = inStream.getChannel();

            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.order(ByteOrder.BIG_ENDIAN);
            chan.read(buf);

            if (!(new String(buf.array()).equals(Head.HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INCORRECT_HEADER"),
                                                              inFile.getName()),
                                         (int)chan.position());
            }

            final String description = readString(chan);
            final String scriptName = readString(chan);

            final String[] mapNames = new String[3];
            for (int i = 0; i < mapNames.length; ++i) {
                mapNames[i] = readString(chan);
            }

            final String spritesheetName = readString(chan);
            chan.position(chan.position() + 5); //skip 8 bytes
            /*
             * TODO:
             * 5 Unknown Byte Structure
             * Four Unknown Bytes
             * An Unknown Byte that has purpose
             */
            buf = ByteBuffer.allocate(3);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            byte red = buf.get();
            byte green = buf.get();
            byte blue = buf.get();
            final Color bgColor = Color.rgb(red, green, blue);

            final String[] tilesetNames = new String[3];
            for (int i = 0; i < tilesetNames.length; ++i) {
                tilesetNames[i] = readString(chan);
                chan.position(chan.position() + 2); //skip 2 bytes after each tileset name
                //First seems to always be 0x02
                //First makes it offset the tiles or something - pulls wrong tiles but from same set if modified
                //Second may relate to parallax but idk
            }

            head = new Head(description, scriptName, mapNames, spritesheetName, bgColor, tilesetNames);

            tileLayers = new TileLayer[3];

            for (int i = 0; i < 3; ++i) {
                buf = ByteBuffer.allocate(8);
                buf.order(ByteOrder.BIG_ENDIAN);
                chan.read(buf);
                buf.flip();

                if (!(new String(buf.array()).equals(TileLayer.HEADER_STRING))) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxPack.INCORRECT_LAYER_HEADER"),
                                                                  i, inFile.getName()),
                                             (int)chan.position());
                }

                buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();
                final int width = buf.getShort();
                final int height = buf.getShort();

                if (width * height > 0) {
                    chan.position(chan.position() + 1);

                    buf = ByteBuffer.allocate(width * height);
                    chan.read(buf);
                    buf.flip();

                    final int[][] tiles = new int[height][width];
                    for (int y = 0; y < tiles.length; ++y) {
                        for (int x = 0; x < tiles[y].length; ++x) {
                            tiles[y][x] = (buf.get((width * y) + x)) & 0xFF; //& 0xFF treats it as unsigned byte when converted to int
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

            entities = new ArrayList <Entity>(numEntities);

            for (int i = 0; i < numEntities; ++i) {
                buf = ByteBuffer.allocate(9);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                final byte flag = buf.get();
                final byte type = buf.get();
                final byte unknownByte = buf.get();
                final int x = buf.getShort();
                final int y = buf.getShort();
                final byte[] data = new byte[2];
                buf.get(data);
                final String name = readString(chan);

                entities.add(new Entity(flag, type, unknownByte, x, y, data, name));
            }
        }
        catch (final FileNotFoundException except) {
            System.err.println("ERROR: Could not locate PXPACK file " + inFile.getName() + ".pxpack");
            //TODO: initialize fields, exit; new file will be made in save() method
        }
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("PxPack.IOEXCEPT"),
                                                       inFile.getName()), except);
        }
        finally {
            try {
                if (null != chan) {
                    chan.close();
                }
                if (null != inStream) {
                    inStream.close();
                }
            }
            catch (final IOException except) {
                Logger.logException(MessageFormat.format(Messages.getString("PxPack.CLOSE_FAIL"), inFile.getName()),
                                    except);
                //TODO: Probably something should be done if the file can't be closed
            }
        }
    }

    public String getName() {
        return file.getName().replace(".pxpack", "");
    }

    //TODO: Make this undo/redo friendly
    public void rename(final String newName) throws IOException {
        final File renamedFile = new File(file.getAbsolutePath() + File.separatorChar + newName + ".pxpack");
        if (renamedFile.exists()) {
            throw new IOException("Attempt to rename " + file.getName() + ".pxpack" + " to " + renamedFile.getName() + ".pxpack" +
                                  " failed because that file already exists!");
        }

        if (!file.renameTo(renamedFile)) {
            throw new IOException("Attempt to rename " + file.getName() + ".pxpack" + " to " + renamedFile.getName() + ".pxpack" +
                                  " failed for an unknown reason");
        }

        //file.delete();
        file = renamedFile;
    }

    public Head getHead() {
        return new Head(head);
    } //TODO: Return clones or make the interior classes immutable

    public TileLayer[] getTileLayers() {
        final TileLayer[] tileLayers = new TileLayer[this.tileLayers.length];
        for (int i = 0; i < tileLayers.length; ++i) {
            tileLayers[i] = new TileLayer(this.tileLayers[i]);
        }
        return tileLayers;
    }

    public ArrayList <Entity> getEntities() {
        ArrayList <Entity> entities = new ArrayList <Entity>(this.entities.size());
        for (final Entity e : this.entities) {
            entities.add(new Entity(e));
        }
        return entities;
    }

    public void setHead(Head head) {
        this.head = new Head(head);
    }

    public void setTileLayer(final int index, final TileLayer layer) {
        tileLayers[index] = new TileLayer(layer);
    }

    public void addEntity(Entity entity) {
        entities.add(new Entity(entity));
    }

    /*public void setEntity(Entity entity) {
        entities.add(new Entity(entity));
    }*/

    /**
     * Reads a string from a file tied to a given FileChannel
     *
     * @param chan The FileChannel object to read from
     *
     * @return The String that was read
     *
     * @throws IOException if there was an error reading the string from the file
     */
    private String readString(FileChannel chan) throws IOException {
        String result = null;
        try {
            ByteBuffer buf = ByteBuffer.allocate(1);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            byte strLen = buf.get();

            buf = ByteBuffer.allocate(strLen);
            buf.order(ByteOrder.BIG_ENDIAN);
            chan.read(buf);
            buf.flip();

            result = new String(buf.array(), "UTF-8");
        }
        catch (final UnsupportedEncodingException except) {
            System.err.println(Messages.getString("PxPack.ReadString.UNSUPPORTED_ENCODING"));
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(MessageFormat.format(Messages.getString("PxPack.ToString.NAME"), file.getName()));

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
        static final String HEADER_STRING = "PXPACK121127a**\0";

        private String description;

        private String scriptName;
        private final String[] mapNames;
        private String spritesheetName;

        private Color bgColor;

        private final String[] tilesetNames;

        //TODO: Limit string lengths and check if bgColor parameter has opacity set; add reset()

        @SuppressWarnings("unused")
        public Head() {
            mapNames = new String[3];
            tilesetNames = new String[3];
        }

        public Head(final Head head) {
            this.description = head.description;
            this.scriptName = head.scriptName;
            this.mapNames = Arrays.copyOf(head.mapNames, head.mapNames.length);
            this.spritesheetName = head.spritesheetName;
            this.bgColor = head.bgColor;
            this.tilesetNames = Arrays.copyOf(head.tilesetNames, head.tilesetNames.length);
        }

        public Head(final String description, final String scriptName, final String[] mapNames, final String spritesheetName,
                    final Color bgColor, final String[] tilesetNames) {

            //TODO: check if these method calls are bad practice
            setDescription(description);
            setScriptName(scriptName);

            this.mapNames = new String[3];
            for (int i = 0; i < 3; ++i) {
                setMapName(i, mapNames[i]);
            }

            setSpritesheetName(spritesheetName);
            this.bgColor = bgColor;

            this.tilesetNames = new String[3];
            for (int i = 0; i < 3; ++i) {
                setTilesetName(i, tilesetNames[i]);
            }
        }

        @SuppressWarnings("unused")
        public String getDescription() {
            return description;
        }

        @SuppressWarnings("unused")
        public String getScriptName() {
            return scriptName;
        }

        @SuppressWarnings("unused")
        public String[] getMapNames() {
            return Arrays.copyOf(mapNames, mapNames.length);
        }

        @SuppressWarnings("unused")
        public String getSpritesheetName() {
            return spritesheetName;
        }

        @SuppressWarnings("unused")
        public Color getBgColor() {
            return bgColor;
        }

        @SuppressWarnings("unused")
        public String[] getTilesetNames() {
            return Arrays.copyOf(tilesetNames, tilesetNames.length);
        }

        @SuppressWarnings("unused")
        public void setDescription(final String description) {
            if (description.length() > 31) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.Setter.ERROR_MSG"),
                                                   "description text", 31));
            }
            this.description = description;
        }

        @SuppressWarnings("unused")
        public void setScriptName(final String scriptName) {
            if (scriptName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.Setter.ERROR_MSG"),
                                                                        "scriptname", 15));
            }
            this.scriptName = scriptName;
        }

        @SuppressWarnings("unused")
        public void setMapName(final int index, String mapName) {
            if (mapName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.NAME_SETTER_ERROR_MSG"),
                                                                        "mapname", 15));
            }
            this.mapNames[index] = mapName;
        }

        @SuppressWarnings("unused")
        public void setSpritesheetName(final String spritesheetName) {
            if (spritesheetName.length() > 15) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Head.NAME_SETTER_ERROR_MSG"),
                                                                        "spritesheet name", 15));
            }
            this.spritesheetName = spritesheetName;
        }

        @SuppressWarnings("unused")
        public void setBgColor(final Color color) {
            //TODO: verify kero blaster doesn't support bg opacity
            if (!color.isOpaque()) {
                throw new IllegalArgumentException(Messages.getString("PxPack.Head.COLOR_SETTER_ERROR_MSG"));
            }
            bgColor = color;
        }

        @SuppressWarnings("unused")
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

            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.SCRIPT_NAME"), scriptName));

            for (int i = 0; i < mapNames.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.MAPNAME"), i, mapNames[i]));
            }

            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.SPRITESHEET_NAME"), spritesheetName));

            //TODO: Format as hex
            result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.BACKGROUND_COLOR"),
                                               bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()));
            for (int i = 0; i < tilesetNames.length; ++i) {
                result.append(MessageFormat.format(Messages.getString("PxPack.Head.ToString.TILESET_NAME"), i, tilesetNames[i]));
            }

            return result.toString();
        }
    }

    public class TileLayer {
        static final String HEADER_STRING = "pxMAP01\0";

        private int[][] tiles;

        public TileLayer() {

        }

        public TileLayer(final TileLayer layer) {
            if (null != layer.tiles) {
                tiles = new int[layer.tiles.length][layer.tiles[0].length];
                for (int y = 0; y < layer.tiles.length; ++y) {
                    /*for (int x = 0; x < layer.tiles[y].length; ++x) {
                        tiles[y][x] = layer.tiles[y][x];
                    }*/
                    System.arraycopy(layer.tiles[y], 0, tiles[y], 0, layer.tiles[y].length);
                }
            }
        }

        public TileLayer(final int[][] tiles) {
            if (tiles.length > 0xFFFF || tiles[0].length > 0xFFFF) {
                throw new IllegalArgumentException(Messages.getString("PxPack.TileLayer.ARR_CONSTRUCTOR_ERROR_MSG"));
            }

            this.tiles = new int[tiles.length][tiles[0].length];
            for (int y = 0; y < tiles.length; ++y) {
                System.arraycopy(tiles[y], 0, this.tiles[y], 0, tiles[y].length);
            }
        }

        /**
         * Resize the tile layer's dimensions
         *
         * @param width  The new width for the layer
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

            if (width == 0 || height == 0) {
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
            else {
                return null;
            }
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

            result.append(MessageFormat.format(Messages.getString("PxPack.TileLayer.ToString.WIDTH"),
                                               String.format("%02X", tiles[0].length)));
            result.append(MessageFormat.format(Messages.getString("PxPack.TileLayer.ToString.HEIGHT"),
                                               String.format("%02X", tiles.length)));

            for (final int[] tile : tiles) {
                result.append('\t');
                for (int j = 0; j < tile.length; ++j) {
                    result.append(String.format("%02X", tile[j]));
                    result.append(' ');
                }
                result.append('\n');
            }
            return result.toString();
        }
    }

    public class Entity {
        private byte flag, type, unknownByte;
        private int x, y;
        private byte[] unknownData;
        private String name;

        @SuppressWarnings("unused")
        public Entity() {
            unknownData = new byte[2];
        }

        public Entity(Entity entity) {
            this.flag = entity.flag;
            this.x = entity.x;
            this.y = entity.y;
            this.unknownData = entity.unknownData;
            this.name = entity.name;
        }

        public Entity(final byte flag, final byte type, final byte unknownByte, final int x, final int y,
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

        @SuppressWarnings("unused")
        public byte getFlag() {
            return flag;
        }

        @SuppressWarnings("unused")
        public byte getType() {
            return type;
        }

        @SuppressWarnings("unused")
        public byte getUnknownByte() {
            return unknownByte;
        }

        @SuppressWarnings("unused")
        public int getX() {
            return x;
        }

        @SuppressWarnings("unused")
        public int getY() {
            return y;
        }

        @SuppressWarnings("unused")
        public byte[] getUnknownData() {
            return Arrays.copyOf(unknownData, unknownData.length);
        }

        @SuppressWarnings("unused")
        public String getName() {
            return name;
        }

        @SuppressWarnings("unused")
        public void setFlag(final byte flag) {
            this.flag = flag;
        }

        @SuppressWarnings("unused")
        public void setType(final byte type) {
            this.type = type;
        }

        @SuppressWarnings("unused")
        public void setUnknownByte(final byte unknownByte) {
            this.unknownByte = unknownByte;
        }

        @SuppressWarnings("unused")
        public void setX(final int x) {
            if (x < 0 || x > 0xFFFF) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Entity.COORDINATE_SETTER_ERROR_MSG"),
                                                                        "x"));
            }
            this.x = x;
        }

        @SuppressWarnings("unused")
        public void setY(final int y) {
            if (x < 0 || y > 0xFFFF) {
                throw new IllegalArgumentException(MessageFormat.format(Messages.getString("PxPack.Entity.COORDINATE_SETTER_ERROR_MSG"),
                                                                        "y"));
            }
            this.y = y;
        }

        @SuppressWarnings("unused")
        public void setUnknownData(final int index, final byte data) {
            this.unknownData[index] = data;
        }

        @SuppressWarnings("unused")
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

            return result.toString();
        }
    }
}