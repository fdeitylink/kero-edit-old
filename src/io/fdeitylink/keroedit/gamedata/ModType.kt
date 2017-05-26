package io.fdeitylink.keroedit.gamedata

/**
 * An enum class representing the different types of Kero Blaster mods
 * that can exist. Each constant within the class represents a different
 * game based on the Kero Blaster engine. Although each is mostly the same
 * in terms of the engine and capabilities, there are some discrepancies
 * that must be dealt with.
 */
enum class ModType {
    /**
     * A mod based on the Pink Hour game
     */
    PINK_HOUR,

    /**
     * A mod based on the Pink Heaven game
     */
    PINK_HEAVEN,

    /**
     * A mod based on the Kero Blaster game
     */
    KERO_BLASTER
}