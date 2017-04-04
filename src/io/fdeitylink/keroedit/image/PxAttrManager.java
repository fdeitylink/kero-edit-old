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

    public static void setAttribute(final String tilesetName, final int x, final int y, final int attribute) {
        final ReadOnlyPxAttrWrapper pxAttrProp = pxAttrsMap.get(tilesetName);
        if (!"mpt00".equals(tilesetName) && pxAttrProp.get() == mpt00) {
            final Path path = Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                        File.separatorChar + "img" + File.separatorChar +
                                        tilesetName + ".pxattr");
            final PxAttr pxAttr = new PxAttr(mpt00, path);
            pxAttrProp.set(pxAttr);
        }
        pxAttrProp.setAttribute(x, y, attribute);
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
                buf.order(ByteOrder.BIG_ENDIAN);
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

        void save() {
            //create file if nonexistent
            System.out.println("pxattr " + path.getFileName() + " saved");
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