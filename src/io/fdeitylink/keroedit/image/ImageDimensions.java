package io.fdeitylink.keroedit.image;

public class ImageDimensions {
    private ImageDimensions() {

    }

    public static final int TILE_WIDTH = 8;
    public static final int TILE_HEIGHT = 8;

    public static final int TILESET_WIDTH = TILE_WIDTH * 16;
    public static final int TILESET_HEIGHT = TILE_HEIGHT * 16;

    public static final int PXATTR_TILE_WIDTH = 16;
    public static final int PXATTR_TILE_HEIGHT = 16;

    public static final int PXATTR_IMAGE_WIDTH = PXATTR_TILE_WIDTH * 16;
    public static final int PXATTR_IMAGE_HEIGHT = PXATTR_TILE_HEIGHT * 16;
}