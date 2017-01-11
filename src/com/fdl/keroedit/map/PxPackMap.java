package com.fdl.keroedit.map;

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

import java.awt.Color;

import com.fdl.keroedit.util.Utilities;
import org.jetbrains.annotations.NotNull;

/**
 * Object for storing information about a PXPACK file
 */
final public class PxPackMap {
    private File file;
    private Head head;
    private TileLayer[] tileLayers;
    private ArrayList <Entity> entities;

    //TODO: Copy constructor?

    /**
     * Constructs a PxPackMap object and parses a given PXPACK file
     * to set the fields of the object
     *
     * @param inFile A File object pointing to a PXPACK file
     */
    public PxPackMap(File inFile) throws IOException, ParseException {
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
                throw new ParseException("ERROR: Incorrect PXPACK header for file " + inFile.getName(), (int)chan.position());
            }

            String description = readString(chan);
            String scriptName = readString(chan);

            String[] mapNames = new String [3];
            for (int i = 0; i < mapNames.length; ++i) {
                mapNames[i] = readString(chan);
            }

            String spritesheetName = readString(chan);
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
            Color bgColor = new Color(red, green, blue);


            String[] tilesetNames = new String [3];
            for (int i = 0; i < tilesetNames.length; ++i) {
                tilesetNames[i] = readString(chan);
                chan.position(chan.position() + 2); //skip 2 bytes after each tileset name
                //First seems to always be 0x02
                //First makes it offset the tiles or something - pulls wrong tiles but from same set if modified
                //Second may relate to parallax but idk
            }

            head = new Head(description, scriptName, mapNames, spritesheetName, bgColor, tilesetNames);

            tileLayers = new TileLayer [3];

            for (int i = 0; i < 3; ++i) {
                buf = ByteBuffer.allocate(8);
                buf.order(ByteOrder.BIG_ENDIAN);
                chan.read(buf);
                buf.flip();

                if (!(new String(buf.array()).equals(TileLayer.HEADER_STRING))) {
                    throw new ParseException("ERROR: Incorrect PXPACK layer header for layer " + i + 1 + " in file " +
                              inFile.getName(), (int)chan.position());
                }

                buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();
                short width = buf.getShort();
                short height = buf.getShort();

                if (width * height > 0) {
                    chan.position(chan.position() + 1);
                    buf = ByteBuffer.allocate(width * height);
                    chan.read(buf);
                    buf.flip();

                    Byte[] tiles = new Byte [width * height];
                    byte[] bufAsArr = buf.array();
                    int j = 0;
                    for (byte b: bufAsArr) {
                        tiles[j++] = b;
                    }

                    tileLayers[i] = new TileLayer(width, height, new ArrayList <Byte>(Arrays.asList(tiles)), i);
                }
                else {
                    tileLayers[i] = new TileLayer();
                }
            }

            buf = ByteBuffer.allocate(2);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            int numEntities = buf.getShort();

            entities = new ArrayList <Entity> (numEntities);

            for (int i = 0; i < numEntities; ++i) {
                buf = ByteBuffer.allocate(9);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                byte flag = buf.get();
                byte type = buf.get();
                byte unknownByte = buf.get();
                short x = buf.getShort();
                short y = buf.getShort();
                byte[] data = new byte [2];
                buf.get(data);
                String name = readString(chan);

                entities.add(new Entity(flag, type, unknownByte, x, y, data, name, i));
            }
        }
        catch (FileNotFoundException except) {
            System.err.println("ERROR: Could not locate PXPACK file " + inFile.getName());
            //TODO: Create default file
        }
        catch (IOException except) {
            reset();
            throw new IOException("ERROR: A problem occurred reading PXPACK file " + inFile.getName(), except);
        }
        catch (ParseException except) {
            reset();
            throw except;
        }
        finally {
            try {
                chan.close();
                inStream.close();
            }
            catch (NullPointerException except) {

            }
            catch (IOException except) {
                System.err.println("Failed to properly close PXPACK file " + inFile.getName());
            }
        }
    }

    public void reset() {
        file = null;
        head = new Head();
        tileLayers = new TileLayer [3];
        entities = new ArrayList <Entity>();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Name: ");
        result.append(file.getName());
        result.append('\n');
        result.append(head.toString());
        result.append('\n');

        for (TileLayer layer : tileLayers) {
            result.append(layer.toString());
            result.append('\n');
        }
        for (Entity entity : entities) {
            result.append(entity.toString());
        }

        return result.toString();
    }

    /**
     * Reads a string from a file tied to a given FileChannel
     *
     * @param chan The FileChannel object to read from
     *
     * @throws IOException if there was an error reading the string from the file
     *
     * @return The String that was read
     */
    private String readString(FileChannel chan) throws IOException {
        String result = null;
        try {
            ByteBuffer buf = ByteBuffer.allocate(1);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            byte strlen = buf.get();

            buf = ByteBuffer.allocate(strlen);
            buf.order(ByteOrder.BIG_ENDIAN);
            chan.read(buf);
            buf.flip();

            result = new String(buf.array(), "UTF-8");
        }
        catch (UnsupportedEncodingException except) {
            System.err.println("Unsupported encoding UTF-8");
        }
        return result;
    }

    final class Head {
        static final String HEADER_STRING = "PXPACK121127a**\0";

        String description;

        String scriptName;
        String[] mapNames;
        String spritesheetName;

        Color bgColor;

        String[] tilesetNames;

        //TODO: Copy constructor

        Head() {
            mapNames = new String[3];
            tilesetNames = new String[3];
        }

        Head(String description, String scriptName, String[] mapNames, String spritesheetName, Color bgColor, String[] tilesetNames) {
            this.description = description;
            this.scriptName = scriptName;
            this.mapNames = Arrays.copyOf(mapNames, 3);
            this.spritesheetName = spritesheetName;
            this.bgColor = bgColor;
            this.tilesetNames = Arrays.copyOf(tilesetNames, 3);
        }

        //TODO: Limit string lengths and check if bgColor parameter has opacity set; add reset()
        @NotNull
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();

            result.append("Description: ");
            result.append(description);
            result.append('\n');

            result.append("Script Name: " );
            result.append(scriptName);
            result.append('\n');

            for (int i = 0; i < mapNames.length; ++i) {
                result.append("Mapname ");
                result.append(i + 1);
                result.append(": ");
                result.append(mapNames[i]);
                result.append('\n');
            }

            result.append("Spritesheet Name: ");
            result.append(spritesheetName);
            result.append('\n');

            result.append("Background Color: \n");

            result.append("\tRed: ");
            result.append(String.format("%02X", bgColor.getRed()));
            result.append('\n');

            result.append("\tGreen: ");
            result.append(String.format("%02X", bgColor.getGreen()));
            result.append('\n');

            result.append("\tBlue: ");
            result.append(String.format("%02X", bgColor.getBlue()));
            result.append('\n');

            for (int i = 0; i < tilesetNames.length; ++i) {
                result.append("Tileset Name ");
                result.append(i + 1);
                result.append(": ");
                result.append(tilesetNames[i]);
                result.append('\n');
            }

            return result.toString();
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
            return Arrays.copyOf(mapNames, 3);
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
            return Arrays.copyOf(tilesetNames, 2);
        }

        @SuppressWarnings("unused")
        public void setDescription(final String description) {
            this.description = description;
        }

        @SuppressWarnings("unused")
        public void setScriptName(final String scriptName) {
            this.scriptName = scriptName;
        }

        @SuppressWarnings("unused")
        public void setMapName(final int index, String mapName) {
            this.mapNames[index] = mapName;
        }

        @SuppressWarnings("unused")
        public void setSpritesheetName(final String spritesheetName) {
            this.spritesheetName = spritesheetName;
        }

        @SuppressWarnings("unused")
        public void setBgColor(final Color color) {
            bgColor = color;
        }

        @SuppressWarnings("unused")
        public void setTilesetName(final int index, String tilesetName) {
            this.tilesetNames[index] = tilesetName;
        }
    }

    //TODO: Add reset()
    final class TileLayer {
        static final String HEADER_STRING = "pxMAP01\0";

        private short width, height;
        private ArrayList <Byte> tiles;

        private int layerNum;

        //TODO: Copy constructor

        @SuppressWarnings("unused")
        TileLayer() {
            width = 0;
            height = 0;
            tiles = new ArrayList <Byte>();

            layerNum = 0;
        }

        TileLayer(final short width, final short height, final ArrayList <Byte> tiles, final int layerNum) {
            this.width = width;
            this.height = height;
            this.tiles = new ArrayList <Byte> (tiles);

            this.layerNum = layerNum;
        }

        /**
         * Resize the tile layer's dimensions
         *
         * @param width The new width for the layer
         * @param height The new height for the layer
         */
        @SuppressWarnings("unused")
        public void resize(final short width, final short height) {
            if (width == this.width && height == this.height) {
                return;
            }

            final short OLD_WIDTH = this.width;
            final short OLD_HEIGHT = this.height;
            this.width = width;
            this.height = height;

            final short LOOP_WIDTH = (width < OLD_WIDTH) ? width : OLD_WIDTH;
            final short LOOP_HEIGHT = (height < OLD_HEIGHT) ? height: OLD_HEIGHT;

            final ArrayList <Byte> OLD_TILES = new ArrayList <Byte>(this.tiles);

            for (int i = 0; i < LOOP_HEIGHT; ++i) {
                for (int j = 0; j < LOOP_WIDTH; ++j) {
                    tiles.set((width * i) + j, OLD_TILES.get((OLD_WIDTH * i) + j));
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();

            result.append("Layer ");
            result.append(layerNum + 1);
            result.append(": ");
            result.append('\n');

            result.append("\tWidth: ");
            result.append(String.format("%02X", width));
            result.append('\n');

            result.append("\tHeight: ");
            result.append(String.format("%02X", height));
            result.append('\n');

            for (int i = 0; i < height; ++i) {
                result.append('\t');
                for (int j = 0; j < width; ++j) {
                    result.append(String.format("%02X", tiles.get((int)Utilities.XYToIndex(j, i, width))));
                    result.append(' ');
                }
                result.append('\n');
            }

            return result.toString();
        }

        @SuppressWarnings("unused")
        public short getWidth() {
            return width;
        }

        @SuppressWarnings("unused")
        public short getHeight() {
            return height;
        }

        @SuppressWarnings("unused")
        public ArrayList <Byte> getTiles() {
            return new ArrayList <Byte>(tiles);
        }

        @SuppressWarnings("unused")
        public int getLayerNum() {
            return layerNum;
        }

        @SuppressWarnings("unused")
        public void setTile(final short x, final short y, final Byte tile) {
            tiles.set((int)Utilities.XYToIndex(x, y, width), tile);
        }

        @SuppressWarnings("unused")
        public void setLayerNum(final int layerNum) {
            this.layerNum = layerNum;
        }
    }

    //TODO: Add reset()
    final class Entity {
        private byte flag, type, unknownByte;
        private short x, y;
        private byte[] data;
        private String name;

        private int entityNum;

        //TODO: Copy constructor

        @SuppressWarnings("unused")
        Entity() {
            data = new byte [2];
        }

        Entity(final byte flag, final byte type, final byte unknownByte, final short x, final short y, final byte[] data, final String name, final int entityNum) {
            this.flag = flag;
            this.type = type;
            this.unknownByte = unknownByte;
            this.x = x;
            this.y = y;
            this.data = Arrays.copyOf(data, 2);
            this.name = name;

            this.entityNum = entityNum;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();

            result.append("Entity ");
            result.append(entityNum);
            result.append(":\n");

            result.append("\tFlag: ");
            result.append(String.format("%02X", flag));
            result.append('\n');

            result.append("\tType: ");
            result.append(String.format("%02X", type));
            result.append('\n');

            result.append("\tUnknown Byte: ");
            result.append(String.format("%02X", unknownByte));
            result.append('\n');

            result.append("\tX Coordinate: ");
            result.append(String.format("%02X", x));
            result.append('\n');

            result.append("\tY Corodinate: ");
            result.append(String.format("%02X", y));
            result.append('\n');

            for (int i = 0; i < data.length; ++i) {
                result.append("\tData: ");
                result.append(i + 1);
                result.append(": ");
                result.append(String.format("%02X", data[i]));
                result.append('\n');
            }

            result.append("\tName: ");
            result.append(name);
            result.append('\n');

            return result.toString();
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
        public short getX() {
            return x;
        }

        @SuppressWarnings("unused")
        public short getY() {
            return y;
        }

        @SuppressWarnings("unused")
        public byte[] getData() {
            return Arrays.copyOf(data, 2);
        }

        @SuppressWarnings("unused")
        public String getName() {
            return name;
        }

        @SuppressWarnings("unused")
        public int getEntityNum() {
            return entityNum;
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
        public void setX(final short x) {
            this.x = x;
        }

        @SuppressWarnings("unused")
        public void setY(final short y) {
            this.y = y;
        }

        @SuppressWarnings("unused")
        public void setData(final int index, final byte data) {
            this.data[index] = data;
        }

        @SuppressWarnings("unused")
        public void setName(final String name) {
            this.name = name;
        }

        @SuppressWarnings("unused")
        public void setEntityNum(final int entityNum) {
            this.entityNum = entityNum;
        }
    }
}