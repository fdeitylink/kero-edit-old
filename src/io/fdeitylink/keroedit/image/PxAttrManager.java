package io.fdeitylink.keroedit.image;

import java.util.HashMap;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.StandardOpenOption;

import java.nio.channels.SeekableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.IOException;
import java.text.ParseException;
import java.io.FileNotFoundException;

import java.text.MessageFormat;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.gamedata.GameData;

/**
 * Manages PXATTR files as they belong to tilesets
 * and are thus shared between maps. As a result, a central
 * repository of them must be kept so that updates to an attribute
 * inside a PXATTR object will be reflected in all maps that use it.
 */
public final class PxAttrManager {
    private static final HashMap <String, ReadOnlyPxAttrWrapper> pxAttrsMap = new HashMap <>();
    private static PxAttr mpt00;

    private PxAttrManager() {

    }

    public static ReadOnlyObjectProperty <PxAttr> getPxAttr(final String tilesetName)
            throws IOException, ParseException {
        if (!GameData.isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve PxAttr file when GameData has not been properly initialized yet");
        }

        if (pxAttrsMap.containsKey(tilesetName)) {
            return pxAttrsMap.get(tilesetName).getReadOnlyProperty();
        }

        Path path = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                              File.separatorChar + "img" + File.separatorChar +
                              tilesetName + ".pxattr");

        final PxAttr pxAttr;

        //if mpt00 pxattr is requested or we need to use it as default
        if ("mpt00".equals(tilesetName) || !Files.exists(path)) {
            if (null == mpt00) {
                path = Paths.get(path.getParent().toAbsolutePath().toString() + File.separatorChar + "mpt00.pxattr");
                if (!Files.exists(path)) { //mpt00 file doesn't exist
                    //return null instead?
                    throw new FileNotFoundException(Messages.getString("PxAttrManager.PxAttr.DEFAULT_MISSING"));
                }
                mpt00 = new PxAttr(path);
            }
            pxAttr = mpt00; //no changes made to mpt00 when used as a default (read code for setAttribute() method)
        }
        else {
            pxAttr = new PxAttr(path);
        }

        final ReadOnlyPxAttrWrapper pxAttrProp = new ReadOnlyPxAttrWrapper(pxAttr);
        pxAttrsMap.put(tilesetName, pxAttrProp);

        return pxAttrProp.getReadOnlyProperty();
    }

    public static void setAttribute(final String tilesetName, final int x, final int y, final int attribute)
            throws IOException {
        final ReadOnlyPxAttrWrapper pxAttrProp = pxAttrsMap.get(tilesetName);

        /*
         * Since mpt00.pxattr is used as a default PXATTR when no PXATTR
         * file exists for a given tileset, if a request to change one
         * of its attributes is made and the tileset mpt00.pxattr is being
         * used for is not mpt00.png, we copy mpt00's attributes into a
         * new PxAttr object with a different file path, and change the
         * attribute on that one. If mpt00 was not being used for this
         * tileset, then the following if block is not run since there
         * is already a specific PXATTR file for this tileset, and the
         * attribute can just be changed on that one.
         */
        if (!"mpt00".equals(tilesetName) && pxAttrProp.get() == mpt00) {
            final Path path = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                        File.separatorChar + "img" + File.separatorChar +
                                        tilesetName + ".pxattr");
            pxAttrProp.set(new PxAttr(mpt00, path));
        }
        pxAttrProp.setAttribute(x, y, attribute);

        //TODO: Supress exception?
        pxAttrProp.get().save();
    }

    public static void wipe() {
        pxAttrsMap.clear();
        mpt00 = null;
    }

    public static final class PxAttr {
        private static final String HEADER_STRING = "pxMAP01\0";

        private final Path path;

        private int[][] attributes;

        PxAttr(Path inPath) throws IOException, ParseException {
            path = inPath;

            /*if (!Files.exists(inPath)) {
                inPath = Paths.get(inPath.getParent().toAbsolutePath().toString() + File.separatorChar + "mpt00.pxattr");
                if (!Files.exists(inPath)) {
                    throw new FileNotFoundException(Messages.getString("PxAttrManager.PxAttr.DEFAULT_MISSING"));
                }
            }*/

            try (SeekableByteChannel chan = Files.newByteChannel(inPath, StandardOpenOption.READ)) {
                ByteBuffer buf = ByteBuffer.allocate(HEADER_STRING.length());
                chan.read(buf);

                if (!(new String(buf.array()).equals(HEADER_STRING))) {
                    throw new ParseException(MessageFormat.format(Messages.getString("PxAttrManager.PxAttr.INCORRECT_HEADER"),
                                                                  inPath.getFileName()),
                                             (int)chan.position());
                }

                buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                //TODO: validate dimensions
                final int width = buf.getShort();
                final int height = buf.getShort();

                if (width * height > 0) {
                    //TODO: Find if it is ever not 0 (might've already checked but make sure)
                    chan.position(chan.position() + 1);

                    buf = ByteBuffer.allocate(width * height);
                    chan.read(buf);
                    buf.flip();

                    attributes = new int[height][width];
                    for (int y = 0; y < height; ++y) {
                        for (int x = 0; x < width; ++x) {
                            attributes[y][x] = buf.get() & 0xFF; //& 0xFF treats it as unsigned byte when converted to int
                        }
                    }
                }
                else {
                    attributes = null;
                }
            }
            catch (final IOException except) {
                throw new IOException(MessageFormat.format(Messages.getString("PxAttrManager.PxAttr.IOEXCEPT"),
                                                           inPath.getFileName()), except);
            }
        }

        //for cloning into new PxAttr
        PxAttr(final PxAttr pxAttr, final Path path) {
            this.path = Paths.get(path.toAbsolutePath().toString()); //is this necessary or can I just do path = pxAttr.path?
            attributes = pxAttr.getAttributes(); //clones
        }

        public int[][] getAttributes() {
            if (null != attributes) {
                final int[][] attributesCopy = new int[attributes.length][attributes[0].length];
                for (int y = 0; y < attributes.length; ++y) {
                    System.arraycopy(attributes[y], 0, attributesCopy[y], 0, attributes[y].length);
                }
                return attributesCopy;
            }
            return null;
        }

        void setAttribute(final int x, final int y, final int attribute) {
            //TODO: Check attribute for validity
            attributes[y][x] = attribute;
        }

        void save() throws IOException {
            try (SeekableByteChannel chan = Files.newByteChannel(path,
                                                                 StandardOpenOption.WRITE,
                                                                 StandardOpenOption.TRUNCATE_EXISTING,
                                                                 StandardOpenOption.CREATE)) {
                ByteBuffer buf = ByteBuffer.wrap(HEADER_STRING.getBytes("SJIS"));
                chan.write(buf);

                if (null == attributes) {
                    buf = ByteBuffer.allocate(4);
                    buf.putShort((short)0).putShort((short)0);
                    buf.flip();

                    chan.write(buf);
                }
                else {
                    short width = (short)attributes[0].length;
                    short height = (short)attributes.length;

                    buf = ByteBuffer.allocate(5);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    buf.putShort(width).putShort(height);
                    buf.put((byte)0);
                    buf.flip();

                    chan.write(buf);

                    buf = ByteBuffer.allocate((width & 0xFFFF) * (height & 0xFFFF)); //& 0xFFFF treats as unsigned when converted to int
                    for (final int[] row : attributes) {
                        for (final int attr : row) {
                            buf.put((byte)attr);
                        }
                    }
                    buf.flip();
                    chan.write(buf);
                }
            }
        }
    }

    private static final class ReadOnlyPxAttrWrapper extends ReadOnlyObjectWrapper <PxAttr> {
        ReadOnlyPxAttrWrapper(final PxAttr pxAttr) {
            super(pxAttr);
        }

        void setAttribute(final int x, final int y, final int attribute) {
            get().setAttribute(x, y, attribute);
            fireValueChangedEvent(); //technically stored property not modified but must alert map tabs to attribute change
        }
    }
}