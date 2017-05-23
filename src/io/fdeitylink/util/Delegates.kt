package io.fdeitylink.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A property delegate that emulates
 * [Delegates.notNull][kotlin.properties.Delegates.notNull] while also
 * allowing delegated properties to ensure that their values are only set
 * to proper values so that invariants can be maintained. As such, any
 * attempts to get the value of the delegated property before it is
 * initialized will throw an [IllegalStateException]. Additionally, any
 * attempts to set the value of the delegated property to an invalid value
 * (checked via a function passed to the constructor for this class) will
 * result in an [IllegalArgumentException]. This essentially emulates
 * having a non-default setter for the delegated property while providing
 * the features of the delegate returned by
 * [Delegates.notNull][kotlin.properties.Delegates.notNull].
 *
 * @param T the type of the delegated property
 *
 * @constructor
 *
 * @param validator a function that will be called to ensure that values
 * given to set() are valid for the delegated property. If it returns true,
 * the given value is valid, otherwise the value is invalid and an
 * [IllegalArgumentException] will be thrown.
 *
 * @param lazyMessage a supplier function that returns an [Any] whose [toString]
 * method will be called to provide a message for any [IllegalArgumentException]s
 * thrown when a value passed to set() is invalid. Appended to that string will be
 * the string " (value: $value)" so the caller knows what was wrong with their input.
 * Defaults to a function returning the String "Invalid value passed to set()".
 */
class NotNullValidated<T: Any>
constructor(private val validator: (T) -> Boolean,
            private val lazyMessage: () -> Any = { "Invalid value passed to set()" }): ReadWriteProperty<Any?, T> {

    private var value: T? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: throw IllegalStateException("Property ${property.name} must be initialized before get()")
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = if (validator(value)) value else throw IllegalArgumentException(lazyMessage().toString() + " (value: $value)")
    }
}