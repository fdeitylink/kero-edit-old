//TODO: Check extension

package com.fdl.keroedit.map;

import java.io.File;
import java.io.FileInputStream;

import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;

import java.util.Arrays;

import java.text.MessageFormat;

import com.fdl.keroedit.Messages;

import com.fdl.keroedit.util.Logger;

public class PxAttr {
    private static final String HEADER_STRING = "pxMAP01\0";

    private final File file;
    //when saving, check if file doesn't exist (meaning we used mpt00.pxattr) - create new one if so

    private int[][] attributes;

    public PxAttr(File inFile) throws IOException, ParseException {
        file = inFile;

        if (!inFile.exists()) {
            inFile = new File(inFile.getParent() + File.separatorChar + "mpt00.pxattr");
            if (!inFile.exists()) {
                throw new FileNotFoundException(Messages.getString("PxAttr.DEFAULT_MISSING"));
            }
        }

        FileInputStream inStream = null;
        FileChannel chan = null;

        try {
            inStream = new FileInputStream(inFile);
            chan = inStream.getChannel();

            ByteBuffer buf = ByteBuffer.allocate(HEADER_STRING.length());
            buf.order(ByteOrder.BIG_ENDIAN);
            chan.read(buf);

            if (!(new String(buf.array()).equals(HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.getString("PxAttr.INCORRECT_HEADER"),
                                                              inFile.getName()),
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
        catch (final IOException except) {
            throw new IOException(MessageFormat.format(Messages.getString("PxAttr.IOEXCEPT"),
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
                Logger.logException(MessageFormat.format(Messages.getString("PxAttr.CLOSE_FAIL"), inFile.getName()),
                                    except);
                //TODO: Probably something should be done if the file can't be closed
            }
        }
    }

    public int[][] getAttributes() {
        return null == attributes ? null : Arrays.copyOf(attributes, attributes.length);
    }

    public void setAttribute(final int x, final int y, final int attribute) {
        //TODO: Check attribute for validity
        attributes[y][x] = attribute;
    }
}
