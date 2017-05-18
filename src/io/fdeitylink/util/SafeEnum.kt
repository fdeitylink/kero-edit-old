package io.fdeitylink.util

/**
 * Marker interface for Enums whose ordinals can be relied on to be unchanged.
 * Useful for enums that are used for array or list indexes.
 */
@Suppress("unused")
interface SafeEnum<E : Enum<E>>