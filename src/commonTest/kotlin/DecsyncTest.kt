package org.decsync.library

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Always return the same mock dir, as different instances cannot communicate
@ExperimentalStdlibApi
class DecsyncMockTest : DecsyncTest(getMockDir().let { { it } }, null)
@ExperimentalStdlibApi
class DecsyncMockTestV1 : DecsyncTest(getMockDir().let { { it } }, DecsyncVersion.V1)

typealias Extra = MutableMap<List<String>, MutableMap<JsonElement, JsonElement>>

@ExperimentalStdlibApi
abstract class DecsyncTest(
        private val dirFactory: () -> NativeFile,
        private val decsyncVersion: DecsyncVersion?
) {
    private val extra1 = mutableMapOf<List<String>, MutableMap<JsonElement, JsonElement>>()
    private val extra2 = mutableMapOf<List<String>, MutableMap<JsonElement, JsonElement>>()

    @BeforeTest
    fun addDecsyncInfo() {
        if (decsyncVersion == null) return
        val infoString = "{\"version\":${decsyncVersion.toInt()}}"
        dirFactory().child(".decsync-info").write(infoString.encodeToByteArray())
    }

    @AfterTest
    fun cleanDirChildren() {
        cleanDir(dirFactory())
        extra1.clear()
        extra2.clear()
    }

    @Test
    fun setAndExecute() {
        val decsync1 = getDecsync()
        val path = listOf("path", "unicode \u263A \uD83C\uDF08", "unsafe `~!@#$%^&*()-_=+/")
        val key = JsonPrimitive("key")
        val value = JsonPrimitive("value")
        decsync1.setEntry(path, key, value)
        checkStoredEntry(decsync1, path, key, value)
        checkStoredEntry(decsync1, path, JsonPrimitive("key-other"), null)
    }

    @Test
    fun setAndGet() {
        val decsync1 = getDecsync("app-id-1")
        val decsync2 = getDecsync("app-id-2")
        val path = listOf("path", "unicode \u263A \uD83C\uDF08", "unsafe `~!@#$%^&*()-_=+/")
        val key = JsonPrimitive("key")
        val value = JsonPrimitive("value")

        decsync1.setEntry(path, key, value)
        checkStoredEntry(decsync2, path, key, null)
        decsync2.executeAllNewEntries(extra2)
        checkExtra(extra2, path, key, value)
        checkStoredEntry(decsync2, path, key, value)
    }

    @Test
    fun asyncSet() {
        val decsync1 = getDecsync("app-id-1")
        val decsync2 = getDecsync("app-id-2")
        val path = listOf("path")
        val key = JsonPrimitive("key")
        val value1 = JsonPrimitive("value1")
        val value2 = JsonPrimitive("value2")
        val datetime1 = "2020-08-23T00:00:00"
        val datetime2 = "2020-08-23T00:00:01"

        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime1, key, value1)))
        decsync2.setEntriesForPath(path, listOf(Decsync.Entry(datetime2, key, value2)))
        checkStoredEntry(decsync1, path, key, value1)
        checkStoredEntry(decsync2, path, key, value2)
        decsync1.executeAllNewEntries(extra1)
        decsync2.executeAllNewEntries(extra2)
        checkExtra(extra1, path, key, value2)
        checkStoredEntry(decsync1, path, key, value2)
        checkExtra(extra2, path, key, null)
        checkStoredEntry(decsync2, path, key, value2)
    }

    @Test
    fun multipleSet() {
        val decsync1 = getDecsync("app-id-1")
        val decsync2 = getDecsync("app-id-2")
        val path = listOf("path")
        val key = JsonPrimitive("key")
        val value1 = JsonPrimitive("value1")
        val value2 = JsonPrimitive("value2")
        val datetime1 = "2020-08-23T00:00:00"
        val datetime2 = "2020-08-23T00:00:01"

        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime1, key, value1)))
        checkStoredEntry(decsync2, path, key, null)
        decsync2.executeAllNewEntries(extra2)
        checkExtra(extra2, path, key, value1)
        checkStoredEntry(decsync2, path, key, value1)
        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime2, key, value2)))
        extra2.clear()
        decsync2.executeAllNewEntries(extra2)
        checkExtra(extra2, path, key, value2)
        checkStoredEntry(decsync2, path, key, value2)
    }

    @Test
    fun doubleSet() {
        val syncType = "sync-type"
        val decsyncDir = dirFactory()
        val localDir = getDecsyncSubdir(decsyncDir, syncType, null).child("local", "app-id")
        val decsync = Decsync<MutableList<String>>(decsyncDir, localDir, syncType, null, "app-id")
        decsync.addListener(emptyList()) { _, entry, extra ->
            extra += entry.datetime
        }
        val path = listOf("path")
        val key = JsonPrimitive("key")
        val value = JsonPrimitive("value")
        val datetime1 = "2020-08-23T00:00:00"
        val datetime2 = "2020-08-23T00:00:01"
        decsync.setEntriesForPath(path, listOf(Decsync.Entry(datetime1, key, value)))
        decsync.setEntriesForPath(path, listOf(Decsync.Entry(datetime2, key, value)))
        val storedEntry = Decsync.StoredEntry(path, key)
        val extra = mutableListOf<String>()
        decsync.executeStoredEntries(listOf(storedEntry), extra)
        assertEquals(listOf(datetime1), extra)
    }

    @Test
    fun listCollections() {
        assertEquals(emptyList(), listDecsyncCollections(dirFactory(), "sync-type"))
        getDecsync("app-id-1", "foo").setEntry(listOf("info"), JsonPrimitive("name"), JsonPrimitive("foo"))
        assertEquals(listOf("foo"), listDecsyncCollections(dirFactory(), "sync-type"))
        getDecsync("app-id-2", "bar").setEntry(listOf("info"), JsonPrimitive("name"), JsonPrimitive("bar"))
        val collections = listDecsyncCollections(dirFactory(), "sync-type")
        assertEquals(setOf("foo", "bar"), collections.toSet())
        assertEquals(collections.toSet().size, collections.size)
    }

    @Test
    fun staticInfo() {
        assertEquals(
                emptyMap(),
                Decsync.getStaticInfo(dirFactory(), "sync-type", null)
        )
        getDecsync("app-id-1").setEntry(listOf("info"), JsonPrimitive("name"), JsonPrimitive("foo"))
        assertEquals(
                mapOf<JsonElement, JsonElement>(
                        JsonPrimitive("name") to JsonPrimitive("foo")
                ),
                Decsync.getStaticInfo(dirFactory(), "sync-type", null)
        )
        getDecsync("app-id-2").setEntry(listOf("info"), JsonPrimitive("color"), JsonPrimitive("bar"))
        assertEquals(
                mapOf<JsonElement, JsonElement>(
                        JsonPrimitive("name") to JsonPrimitive("foo"),
                        JsonPrimitive("color") to JsonPrimitive("bar")
                ),
                Decsync.getStaticInfo(dirFactory(), "sync-type", null)
        )
    }

    private fun getDecsync(ownAppId: String = "app-id", collection: String? = null): Decsync<Extra> {
        val syncType = "sync-type"
        val decsyncDir = dirFactory()
        val localDir = getDecsyncSubdir(decsyncDir, syncType, collection).child("local", ownAppId)
        val decsync = Decsync<Extra>(decsyncDir, localDir, syncType, collection, ownAppId)
        decsync.addListener(emptyList()) { path, entry, extra ->
            val map = extra.getOrPut(path) { mutableMapOf() }
            map[entry.key] = entry.value
        }
        return decsync
    }

    private fun checkStoredEntry(decsync: Decsync<Extra>, path: List<String>, key: JsonElement, value: JsonElement?) {
        val storedEntry = Decsync.StoredEntry(path, key)
        val extra = mutableMapOf<List<String>, MutableMap<JsonElement, JsonElement>>()
        decsync.executeStoredEntries(listOf(storedEntry), extra)
        assertEquals(value, extra[storedEntry.path]?.get(storedEntry.key))
    }

    private fun checkExtra(extra: Extra, path: List<String>, key: JsonElement, value: JsonElement?) {
        assertEquals(value, extra[path]?.get(key))
    }
}