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

import io.fdeitylink.keroedit.util.Logger;

import io.fdeitylink.keroedit.gamedata.GameData;

/**
 * Class for managing PXATTR files as they belong to tilesets
 * and are thus shared between maps. As a result, a central
 * repository of them must be kept so that updates to an attribute
 * inside a PXATTR object will be reflected in all maps depending upon it.
 */
public class PxAttrManager {
    private static final HashMap <String, ReadOnlyPxAttrWrapper> pxAttrsMap = new HashMap <>();

    private PxAttrManager() {

    }

    public static ReadOnlyObjectProperty <PxAttr> getPxAttr(final String tilesetName) throws IOException, ParseException {
        if (pxAttrsMap.containsKey(tilesetName)) {
            return pxAttrsMap.get(tilesetName).getReadOnlyProperty();
        }
        final PxAttr pxAttr = new PxAttr(Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                   File.separatorChar + "img" + File.separatorChar +
                                                   tilesetName + ".pxattr"));

        final ReadOnlyPxAttrWrapper pxAttrProp = new ReadOnlyPxAttrWrapper(pxAttr);
        pxAttrsMap.put(tilesetName, pxAttrProp);

        return pxAttrProp.getReadOnlyProperty();
    }

    public static void setAttribute(final String pxAttrName, final int x, final int y, final int attribute) {
        pxAttrsMap.get(pxAttrName).setAttribute(x, y, attribute);
    }

    public static class PxAttr {
        private static final String HEADER_STRING = "pxMAP01\0";

        private final Path path;
        //when saving, check if file doesn't exist (meaning we used mpt00.pxattr as default) - create new one if so

        private int[][] attributes;

        private PxAttr(Path inPath) throws IOException, ParseException {
            path = inPath;

            if (!Files.exists(inPath)) {
                inPath = Paths.get(inPath.getParent().toAbsolutePath().toString() + File.separatorChar + "mpt00.pxattr");
                if (!Files.exists(inPath)) {
                    throw new FileNotFoundException(Messages.getString("PxAttrManager.PxAttr.DEFAULT_MISSING"));
                }
            }

            try (SeekableByteChannel chan =Files.newByteChannel(inPath,StandardOpenOption.READ)) {
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
            catch( final IOException except){
                throw new IOException(MessageFormat.format(Messages.getString("PxAttrManager.PxAttr.IOEXCEPT"),
                                                           inPath.getFileName()), except);
            }
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

        private void setAttribute(final int x, final int y, final int attribute) {
            //TODO: Check attribute for validity
            attributes[y][x] = attribute;
        }

        private void save() {
            System.out.println("pxattr " + path.getFileName() + " saved");
        }
    }

    private static class ReadOnlyPxAttrWrapper extends ReadOnlyObjectWrapper <PxAttr> {
        ReadOnlyPxAttrWrapper(final PxAttr pxAttr) {
            super(pxAttr);
        }

        void setAttribute(final int x, final int y, final int attribute) {
            get().setAttribute(x, y, attribute);
            fireValueChangedEvent(); //technically stored property not modified but must alert map tabs to attribute change
        }
    }
}