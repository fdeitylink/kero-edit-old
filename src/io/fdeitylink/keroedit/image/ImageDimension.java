package io.fdeitylink.keroedit.image;

//TODO: Implement as enum ... maybe
public final class ImageDimension {
    public static final int TILE_WIDTH = 8;
    public static final int TILE_HEIGHT = 8;

    public static final int TILESET_WIDTH = TILE_WIDTH * 16;
    public static final int TILESET_HEIGHT = TILE_HEIGHT * 16;

    public static final int PXATTR_TILE_WIDTH = 16;
    public static final int PXATTR_TILE_HEIGHT = 16;

    public static final int PXATTR_IMAGE_WIDTH = PXATTR_TILE_WIDTH * 16;
    public static final int PXATTR_IMAGE_HEIGHT = PXATTR_TILE_HEIGHT * 16;

    private ImageDimension() {

    }
}