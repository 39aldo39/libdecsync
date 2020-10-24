package org.decsync.library

import kotlinx.serialization.json.JsonElement

/**
 * Represents an item with multiple properties that are stored by DecSync. This interface allows for
 * abstract reasoning over synchronized items. Mainly used by [DecsyncObserver].
 *
 * @property type the name of the type of item, for example "RssArticle".
 * @property id the identifier of the item.
 * @property idStoredEntry the location where the identifier is stored by DecSync. Its value is
 * always a boolean denoting the existence of the item. Null if this is not stored.
 * @property entries all the entries stored by DecSync. It is a map from the stored location (a
 * combination of the path and key) to the stored value. The value is expressed as a [Value].
 */
@ExperimentalStdlibApi
interface DecsyncItem {
    val type: String
    val id: Comparable<*>
    val idStoredEntry: Decsync.StoredEntry?
    val entries: Map<Decsync.StoredEntry, Value>

    /**
     * Represents a value stored by DecSync. It also knows its default value. This is used when an
     * item is inserted: unknown entries have to be equal to their default values for DecSync to
     * work correctly.
     *
     * For example, if a RSS article is inserted, its 'read' status has be false by default. This
     * ensures that this status will be updated correctly regarding the other devices.
     */
    sealed class Value {
        /**
         * Represents a normal value: its value is equal to [value] while its default values is
         * equal to [default].
         */
        class Normal(val value: JsonElement, val default: JsonElement) : Value() {
            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other == null || other !is Normal) return false
                return value == other.value
            }

            override fun hashCode(): Int = value.hashCode()
        }

        /**
         * Represents a reference to another item. As the application may use a different identifier
         * than the one specified by DecSync, it can give its own in [internalId]. Furthermore, it
         * may require a database lookup to get the DecSync identifier. This lookup is executed only
         * when necessary by the function [value]. There are two additional assumptions:
         * - The DecSync identifiers are equal if and only if their internalIds are.
         * - The default value is null.
         */
        class Reference(val internalId: Any?, val value: () -> JsonElement) : Value() {
            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other == null || other !is Reference) return false
                return internalId == other.internalId
            }

            override fun hashCode() = internalId.hashCode()
        }

        fun isDefault() = when (this) {
            is Normal -> value == default
            is Reference -> internalId == null
        }

        fun toJson(): JsonElement = when (this) {
            is Normal -> value
            is Reference -> value()
        }
    }
}