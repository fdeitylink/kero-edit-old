package io.fdeitylink.keroedit.image

import java.io.File

import java.nio.file.Paths
import java.nio.file.Files

import java.io.IOException
import java.text.ParseException
import java.nio.file.NoSuchFileException

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper

import io.fdeitylink.keroedit.gamedata.GameData

object PxAttrManager {
    private val pxAttrMap = hashMapOf<String, ReadOnlyPxAttrWrapper>()

    //TODO: Verify mpt00 is the default PXATTR file
    private var mpt00: PxAttr? = null

    @Throws(IOException::class, ParseException::class)
    fun getPxAttr(tilesetName: String): ReadOnlyObjectProperty<PxAttr> {
        if (!GameData.isInitialized) {
            throw IllegalStateException("GameData must be initialized before PXATTRs can be retrieved from PxAttrManager")
        }

        if (pxAttrMap.contains(tilesetName)) {
            return pxAttrMap[tilesetName]!!.readOnlyProperty
        }

        var path = Paths.get(GameData.resourceFolder.toString() +
                             File.separatorChar + GameData.imageFolder + File.separatorChar +
                             tilesetName + GameData.pxAttrExtension)

        //If the mpt00 PXATTR was requested or it needs to be used as a default
        val pxAttr = if ("mpt00" == tilesetName || !Files.exists(path)) {
            if (null == mpt00) {
                /*
                 * TODO:
                 * Use path.resolveSibling()
                 * Don't throw except - return a PXATTR with all empty values
                 */
                path = Paths.get(path.parent.toAbsolutePath().toString() + File.separatorChar +
                                 "mpt00" + GameData.pxAttrExtension)
                if (!Files.exists(path)) {
                    //The default PXATTR file mpt00 does not exist
                    /*
                     * TODO:
                     * Return null instead?
                     * Return PxAttr with all empty attributes?
                     */
                    throw NoSuchFileException(path.toString(), null, "Missing default PXATTR file mpt00")
                }

                mpt00 = PxAttr(path)
            }

            /*
             * Note that no changes are ever made to the mpt00 PXATTR
             * if it is used as a default. Read the code for the
             * setAttribute() method
             */
            mpt00!!
        }
        else {
            PxAttr(path)
        }

        val prop = ReadOnlyPxAttrWrapper(pxAttr)
        pxAttrMap.put(tilesetName, prop)

        return prop.readOnlyProperty
    }

    @Throws(IOException::class)
    fun setAttribute(tilesetName: String, x: Int, y: Int, attribute: Int) {
        require(pxAttrMap.contains(tilesetName))
        { "Given tileset has no stored PxAttr object yet (tilesetName: $tilesetName)" }

        val prop = pxAttrMap[tilesetName]!!

        /*
         * mpt00.pxattr is used as the default PXATTR when no PXATTR
         * file exists for a given tileset. As such, if a request is
         * to change a property in a PXATTR file, we must check if
         * mpt00 is the PXATTR being used for the tileset. If it is,
         * we check if it is being used as a default (if being used
         * as a default, tilesetName will be different from "mpt00").
         * If it is in fact being used as a default, copy the properties
         * of the mpt00 PXATTR into a new PXATTR and make the attribute
         * changes on that new PXATTR. If mpt00 wasn't being used, or
         * the tileset is mpt00, then we needn't make any new PXATTR
         * and the attribute is simply changed on the PXATTR already
         * corresponding to tilesetName
         */
        if (prop.get() === mpt00 && "mpt00" != tilesetName) {
            prop.set(PxAttr(mpt00!!, Paths.get(GameData.resourceFolder.toString() +
                                             File.separatorChar + GameData.imageFolder + File.separatorChar +
                                             tilesetName + GameData.pxAttrExtension)))
        }
        prop.setAttribute(x, y, attribute)
        prop.get().save()
    }

    /**
     * Clears all [PxAttr] objects stored by this object.
     */
    fun wipe() {
        //TODO: Also notify any MapEditTabs? This is only called when they are all closed but it doesn't hurt for safety
        pxAttrMap.clear()
        mpt00 = null
    }

    /*
     * TODO:
     * Use type alias?
     * Use Kotlin observable properties?
     */
    private class ReadOnlyPxAttrWrapper(pxAttr: PxAttr): ReadOnlyObjectWrapper<PxAttr>(pxAttr) {
        fun setAttribute(x: Int, y: Int, attribute: Int) {
            get().setAttribute(x, y, attribute)
            /*
             * This only triggers InvalidationListeners (not ChangeListeners)
             * because the actual PxAttr object stored by this property is unchanged
             */
            fireValueChangedEvent()
        }
    }
}