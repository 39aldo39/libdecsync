package org.decsync.library.items

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.decsync.library.Decsync.StoredEntry
import org.decsync.library.DecsyncItem
import org.decsync.library.DecsyncItem.Value.Normal
import org.decsync.library.DecsyncItem.Value.Reference

@ExperimentalStdlibApi
object Rss {
    open class Article(
            guid: String,
            read: Boolean,
            marked: Boolean,
            private val year: Int,
            private val month: Int,
            private val day: Int
    ) : DecsyncItem {
        override val type = "RssArticle"
        override val id = guid
        override val idStoredEntry: StoredEntry? = null
        override val entries = mapOf(
                StoredEntry(getDecsyncPath("read"), JsonPrimitive(guid)) to
                        Normal(JsonPrimitive(read), JsonPrimitive(false)),
                StoredEntry(getDecsyncPath("marked"),  JsonPrimitive(guid)) to
                        Normal(JsonPrimitive(marked), JsonPrimitive(false))
        )

        private fun getDecsyncPath(type: String): List<String> {
            val yearString = year.toString()
            val monthString = month.toString().padStart(2, '0')
            val dayString = day.toString().padStart(2, '0')
            return listOf("articles", type, yearString, monthString, dayString)
        }
    }

    open class Feed(
            link: String,
            name: String?,
            internalCatId: Any?,
            category: () -> String?
    ) : DecsyncItem {
        override val type = "RssFeed"
        override val id = link
        override val idStoredEntry = StoredEntry(listOf("feeds", "subscriptions"), JsonPrimitive(link))
        override val entries = mapOf(
                StoredEntry(listOf("feeds", "names"), JsonPrimitive(link)) to
                        Normal(JsonPrimitive(name), JsonNull),
                StoredEntry(listOf("feeds", "categories"), JsonPrimitive(link)) to
                        Reference(internalCatId) { JsonPrimitive(category()) }
        )
    }

    open class Category(
            catId: String,
            name: String,
            internalParentId: Any?,
            parent: () -> String?
    ) : DecsyncItem {
        override val type = "RssCategory"
        override val id = catId
        override val idStoredEntry: StoredEntry? = null
        override val entries = mapOf(
                StoredEntry(listOf("categories", "names"), JsonPrimitive(catId)) to
                        Normal(JsonPrimitive(name), JsonPrimitive(catId)),
                StoredEntry(listOf("categories", "parents"), JsonPrimitive(catId)) to
                        Reference(internalParentId) { JsonPrimitive(parent()) }

        )
    }
}