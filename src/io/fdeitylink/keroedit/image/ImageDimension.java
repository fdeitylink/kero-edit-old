package io.fdeitylink.keroedit.image;

//TODO: Implement as enum...maybe
public final class ImageDimension {
    public static final int TILE_WIDTH = 8;
    public static final int TILE_HEIGHT = 8;

    public static final int TILES_PER_ROW = 16;
    public static final int TILES_PER_COLUMN = 16;

    public static final int TILESET_WIDTH = TILE_WIDTH * TILES_PER_ROW;
    public static final int TILESET_HEIGHT = TILE_HEIGHT * TILES_PER_COLUMN;

    public static final int PXATTR_TILE_WIDTH = 16;
    public static final int PXATTR_TILE_HEIGHT = 16;

    public static final int PXATTR_TILES_PER_ROW = 16;
    public static final int PXATTR_TILES_PER_COLUMN = 8;

    public static final int PXATTR_IMAGE_WIDTH = PXATTR_TILE_WIDTH * PXATTR_TILES_PER_ROW;
    public static final int PXATTR_IMAGE_HEIGHT = PXATTR_TILE_HEIGHT * PXATTR_TILES_PER_COLUMN;

    public static final int ENTITY_WIDTH = 16;
    public static final int ENTITY_HEIGHT = 16;

    public static final int ENTITIES_PER_ROW = 16;
    public static final int ENTITIES_PER_COLUMN = 11;

    public static final int ENTITY_IMAGE_WIDTH = ENTITY_WIDTH * ENTITIES_PER_ROW;
    public static final int ENTITY_IMAGE_HEIGHT = ENTITY_HEIGHT * ENTITIES_PER_COLUMN;

    private ImageDimension() {

    }
}