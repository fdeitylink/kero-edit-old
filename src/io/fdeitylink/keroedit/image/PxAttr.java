package io.fdeitylink.keroedit.image;

import java.text.MessageFormat;

import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.file.StandardOpenOption;

import java.nio.channels.SeekableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.IOException;
import java.text.ParseException;

import io.fdeitylink.util.NullArgumentException;

import io.fdeitylink.keroedit.Messages;

//TODO: Move back to PxAttrManager as static inner class?
public final class PxAttr {
    private static final String HEADER_STRING = "pxMAP01\0";

    private final Path path;

    private int[][] attributes;

    PxAttr(final Path inPath) throws IOException, ParseException {
        path = NullArgumentException.Companion.requireNonNull(inPath, "PxAttr", "inPath").toAbsolutePath();

        /*if (!Files.exists(inPath)) {
            inPath = Paths.get(inPath.getParent().toAbsolutePath().toString() + File.separatorChar + "mpt00" + GameData.pxAttrExtension);
            if (!Files.exists(inPath)) {
                throw new FileNotFoundException(Messages.INSTANCE.getString("PxAttrManager.PxAttr.DEFAULT_MISSING"));
            }
        }*/

        try (SeekableByteChannel chan = Files.newByteChannel(inPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(HEADER_STRING.length());
            chan.read(buf);

            if (!(new String(buf.array()).equals(HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.INSTANCE.getString("PxAttrManager.PxAttr.INCORRECT_HEADER"),
                                                              inPath.getFileName()),
                                         (int)chan.position());
            }

            buf = ByteBuffer.allocate(4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            //TODO: validate dimensions/size
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
            throw new IOException(MessageFormat.format(Messages.INSTANCE.getString("PxAttrManager.PxAttr.IOEXCEPT"),
                                                       inPath.getFileName()), except);
        }
    }

    //for cloning into new PxAttr
    PxAttr(final PxAttr pxAttr, final Path inPath) {
        attributes = NullArgumentException.Companion.requireNonNull(pxAttr, "PxAttr", "pxAttr").getAttributes();
        path = NullArgumentException.Companion.requireNonNull(inPath, "PxAttr", "inPath").toAbsolutePath();
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
        //TODO: Check attribute for validity (range?)
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
