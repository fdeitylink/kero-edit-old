package io.fdeitylink.keroedit.image;

import java.util.HashMap;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;
import java.text.ParseException;
import java.io.FileNotFoundException;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import io.fdeitylink.keroedit.util.NullArgumentException;

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

    //TODO: Verify mpt00 is the default PXATTR file
    private static PxAttr mpt00;

    private PxAttrManager() {

    }

    public static ReadOnlyObjectProperty <PxAttr> getPxAttr(final String tilesetName)
            throws IOException, ParseException {
        if (!GameData.isInitialized()) {
            throw new IllegalStateException("Attempt to retrieve PxAttr file when GameData has not been properly initialized yet");
        }

        NullArgumentException.requireNonNull(tilesetName, "getPxAttr", "tilesetName");

        if (pxAttrsMap.containsKey(tilesetName)) {
            return pxAttrsMap.get(tilesetName).getReadOnlyProperty();
        }

        Path path = Paths.get(GameData.getResourceFolder().toString() +
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
        NullArgumentException.requireNonNull(tilesetName, "setAttribute", "tilesetName");

        if (!pxAttrsMap.containsKey(tilesetName)) {
            throw new IllegalArgumentException("Attempt to set attribute for PxAttr when given tileset has no stored PxAttr object " +
                                               "(tilesetName: " + tilesetName + ')');
        }

        final ReadOnlyPxAttrWrapper pxAttrProp = pxAttrsMap.get(tilesetName);

        /*
         * Since mpt00.pxattr is used as a default PXATTR when no PXATTR
         * file exists for a given tileset, if a request to change one
         * of its attributes is made and the tileset mpt00.pxattr is being
         * used for is not mpt00.png, we copy mpt00's attributes into a
         * new PxAttr object with a different file path, and change the
         * attribute on that one.
         * If mpt00 was not being used for this tileset, then there was
         * already a specific PXATTR file for this tileset, and the
         * attribute can just be changed on that one.
         */
        if (!"mpt00".equals(tilesetName) && pxAttrProp.get() == mpt00) {
            pxAttrProp.set(new PxAttr(mpt00, Paths.get(GameData.getResourceFolder().toAbsolutePath().toString() +
                                                       File.separatorChar + "img" + File.separatorChar +
                                                       tilesetName + ".pxattr")));
        }
        pxAttrProp.setAttribute(x, y, attribute);
        pxAttrProp.get().save();
    }

    public static void wipe() {
        pxAttrsMap.clear();
        mpt00 = null;
    }

    private static final class ReadOnlyPxAttrWrapper extends ReadOnlyObjectWrapper <PxAttr> {
        ReadOnlyPxAttrWrapper(final PxAttr pxAttr) {
            super(pxAttr);
        }

        void setAttribute(final int x, final int y, final int attribute) {
            get().setAttribute(x, y, attribute);
            /*
             * This only triggers InvalidationListeners (not ChangeListeners)
             * because the PxAttr object stored by this property is not changed
             */
            fireValueChangedEvent();
        }
    }
}