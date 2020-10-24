package org.decsync.library.items

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.decsync.library.Decsync.StoredEntry
import org.decsync.library.DecsyncItem
import org.decsync.library.DecsyncItem.Value.Normal

@ExperimentalStdlibApi
object Contacts {
    open class Info(collection: String, name: String, deleted: Boolean) : DecsyncItem {
        override val type = "ContactsInfo"
        override val id = "info"
        override val idStoredEntry: StoredEntry? = null
        override val entries = mapOf(
                StoredEntry(listOf("info"), JsonPrimitive("name")) to
                        Normal(JsonPrimitive(name), JsonPrimitive(collection)),
                StoredEntry(listOf("info"), JsonPrimitive("deleted")) to
                        Normal(JsonPrimitive(deleted), JsonPrimitive(false))
        )
    }

    open class Resource(uid: String, vcard: String?) : DecsyncItem {
        override val type = "ContactsResource"
        override val id = uid
        override val idStoredEntry: StoredEntry? = null
        override val entries = mapOf(
                StoredEntry(listOf("resources", uid), JsonNull) to
                        Normal(JsonPrimitive(vcard), JsonNull)
        )
    }
}