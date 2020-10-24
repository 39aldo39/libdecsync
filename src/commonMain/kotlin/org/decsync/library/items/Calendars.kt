package org.decsync.library.items

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.decsync.library.Decsync.StoredEntry
import org.decsync.library.DecsyncItem
import org.decsync.library.DecsyncItem.Value.Normal

@ExperimentalStdlibApi
object Calendars {
    open class Info(collection: String, name: String, deleted: Boolean, color: String) : DecsyncItem {
        override val type = "CalendarsInfo"
        override val id = "info"
        override val idStoredEntry: StoredEntry? = null
        override val entries = mapOf(
                StoredEntry(listOf("info"), JsonPrimitive("name")) to
                        Normal(JsonPrimitive(name), JsonPrimitive(collection)),
                StoredEntry(listOf("info"), JsonPrimitive("deleted")) to
                        Normal(JsonPrimitive(deleted), JsonPrimitive(false)),
                StoredEntry(listOf("info"), JsonPrimitive("color")) to
                        Normal(JsonPrimitive(color), JsonNull)
        )
    }

    open class Resource(uid: String, ical: String?) : DecsyncItem {
        override val type = "CalendarsResource"
        override val id = uid
        override val idStoredEntry: StoredEntry? = null
        override val entries = mapOf(
                StoredEntry(listOf("resources", uid), JsonNull) to
                        Normal(JsonPrimitive(ical), JsonNull)
        )
    }
}