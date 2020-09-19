package org.decsync.library

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Always return the same mock dir, as different instances cannot communicate
@ExperimentalStdlibApi
class DecsyncMockTest : DecsyncTest(getMockDir().let { { it } }, null)
@ExperimentalStdlibApi
class DecsyncMockTestV1 : DecsyncTest(getMockDir().let { { it } }, DecsyncVersion.V1)
@ExperimentalStdlibApi
class DecsyncMockTestV2 : DecsyncTest(getMockDir().let { { it } }, DecsyncVersion.V2)

@ExperimentalStdlibApi
class DecsyncUpgradeMockTestV1V2 : DecsyncUpgradeTest(
        getMockDir().let { { it } },
        DecsyncVersion.V1, DecsyncVersion.V2
)

typealias Extra = MutableMap<List<String>, MutableMap<JsonElement, JsonElement>>

@ExperimentalStdlibApi
abstract class DecsyncTestHelpers(
        protected val dirFactory: () -> NativeFile,
        protected val decsyncVersion: DecsyncVersion?
) {
    protected val extra1 = mutableMapOf<List<String>, MutableMap<JsonElement, JsonElement>>()
    protected val extra2 = mutableMapOf<List<String>, MutableMap<JsonElement, JsonElement>>()

    @BeforeTest
    fun addDecsyncInfo() {
        writeDecsyncInfo(dirFactory(), decsyncVersion)
    }

    @AfterTest
    fun cleanDirChildren() {
        cleanDir(dirFactory())
        extra1.clear()
        extra2.clear()
    }

    protected fun writeDecsyncInfo(dir: NativeFile, version: DecsyncVersion?) {
        if (version == null) return
        val infoString = "{\"version\":${version.toInt()}}"
        dir.child(".decsync-info").write(infoString.encodeToByteArray())
    }

    protected fun getDecsync(ownAppId: String = "app-id", collection: String? = null): Decsync<Extra> {
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

    protected fun checkStoredEntry(decsync: Decsync<Extra>, path: List<String>, key: JsonElement, value: JsonElement?) {
        val storedEntry = Decsync.StoredEntry(path, key)
        val extra = mutableMapOf<List<String>, MutableMap<JsonElement, JsonElement>>()
        decsync.executeStoredEntries(listOf(storedEntry), extra)
        assertEquals(value, extra[storedEntry.path]?.get(storedEntry.key))
    }

    protected fun checkExtra(extra: Extra, path: List<String>, key: JsonElement, value: JsonElement?) {
        assertEquals(value, extra[path]?.get(key))
    }
}

@ExperimentalStdlibApi
abstract class DecsyncTest(
        dirFactory: () -> NativeFile,
        decsyncVersion: DecsyncVersion?
) : DecsyncTestHelpers(dirFactory, decsyncVersion) {
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

    @Test
    fun entriesCount() {
        val decsync1 = getDecsync("app-id-1")
        val decsync2 = getDecsync("app-id-2")
        val path = listOf("path")
        val pathOther = listOf("pathOther")
        val key1 = JsonPrimitive("key1")
        val key2 = JsonPrimitive("key2")
        val value = JsonPrimitive("value")
        val datetime1 = "2020-08-23T00:00:00"
        val datetime2 = "2020-08-23T00:00:01"

        assertEquals(0, Decsync.getEntriesCount(dirFactory(), "sync-type", null, listOf("path")))
        decsync1.setEntriesForPath(pathOther, listOf(Decsync.Entry(datetime1, key1, value)))
        assertEquals(0, Decsync.getEntriesCount(dirFactory(), "sync-type", null, listOf("path")))
        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime1, key1, value)))
        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime1, key2, value)))
        assertEquals(2, Decsync.getEntriesCount(dirFactory(), "sync-type", null, listOf("path")))
        decsync2.setEntriesForPath(path, listOf(Decsync.Entry(datetime2, key1, JsonNull)))
        assertEquals(1, Decsync.getEntriesCount(dirFactory(), "sync-type", null, listOf("path")))
    }

}

@ExperimentalStdlibApi
abstract class DecsyncUpgradeTest(
    dirFactory: () -> NativeFile,
    decsyncVersion: DecsyncVersion,
    private val decsyncVersionNew: DecsyncVersion
) : DecsyncTestHelpers(dirFactory, decsyncVersion) {

    private fun upgradeDecsyncInfo() {
        writeDecsyncInfo(dirFactory(), decsyncVersionNew)
    }

    @Test
    fun basic() {
        val decsync1 = getDecsync("app-id-1")
        val path = listOf("path")
        val key = JsonPrimitive("key")
        val value = JsonPrimitive("value")
        decsync1.setEntry(path, key, value)
        checkStoredEntry(decsync1, path, key, value)

        upgradeDecsyncInfo()

        val decsync2 = getDecsync("app-id-2")
        decsync2.initStoredEntries()
        checkStoredEntry(decsync2, path, key, value)
        decsync1.executeAllNewEntries(extra1)
        checkStoredEntry(decsync1, path, key, value)
        decsync2.executeAllNewEntries(extra2)
        checkStoredEntry(decsync2, path, key, value)
    }

    @Test
    fun independentUpgrade() {
        val decsync1 = getDecsync("app-id-1")
        val path = listOf("path")
        val key = JsonPrimitive("key")
        val value = JsonPrimitive("value")
        decsync1.setEntry(path, key, value)

        upgradeDecsyncInfo()

        val decsync2 = getDecsync("app-id-2")
        decsync2.initStoredEntries()
        decsync2.executeAllNewEntries(extra2)
        checkStoredEntry(decsync2, path, key, value)
    }

    @Test
    fun oldValue() {
        val decsync1 = getDecsync("app-id-1")
        val path = listOf("path")
        val key = JsonPrimitive("key")
        val value1 = JsonPrimitive("value1")
        val value2 = JsonPrimitive("value2")
        val value3 = JsonPrimitive("value3")
        val datetime1 = "2020-08-23T00:00:01"
        val datetime2 = "2020-08-23T00:00:02"
        val datetime3 = "2020-08-23T00:00:03"
        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime1, key, value1)))

        upgradeDecsyncInfo()

        val decsync2 = getDecsync("app-id-2")
        decsync2.executeAllNewEntries(extra2)
        // Now decsync1 uses the old version, while decsync2 uses the new version

        decsync2.setEntriesForPath(path, listOf(Decsync.Entry(datetime2, key, value2)))
        checkStoredEntry(decsync1, path, key, value1)
        checkStoredEntry(decsync2, path, key, value2)

        decsync1.setEntriesForPath(path, listOf(Decsync.Entry(datetime3, key, value3)))
        checkStoredEntry(decsync1, path, key, value3)
        checkStoredEntry(decsync2, path, key, value2) // We don't get updates from old versions
    }

    @Test
    fun oldInfo() {
        val decsync1 = getDecsync("app-id-1")
        val key = JsonPrimitive("name")
        val value1 = JsonPrimitive("Foo")
        val value2 = JsonPrimitive("Bar")
        val value3 = JsonPrimitive("Baz")
        val datetime1 = "2020-08-23T00:00:01"
        val datetime2 = "2020-08-23T00:00:02"
        val datetime3 = "2020-08-23T00:00:03"
        decsync1.setEntriesForPath(listOf("info"), listOf(Decsync.Entry(datetime1, key, value1)))
        assertEquals(
                mapOf<JsonElement, JsonElement>(
                        JsonPrimitive("name") to value1
                ),
                Decsync.getStaticInfo(dirFactory(), "sync-type", null)
                        .filter { !it.key.jsonPrimitive.content.startsWith("last-active-") }
        )

        upgradeDecsyncInfo()

        val decsync2 = getDecsync("app-id-2")
        decsync2.executeAllNewEntries(extra2)
        decsync2.setEntriesForPath(listOf("info"), listOf(Decsync.Entry(datetime2, key, value2)))
        assertEquals(
                mapOf<JsonElement, JsonElement>(
                        JsonPrimitive("name") to value2
                ),
                Decsync.getStaticInfo(dirFactory(), "sync-type", null)
                        .filter { !it.key.jsonPrimitive.content.startsWith("last-active-") }
        )

        // decsync1 still uses the old DecSync version, but we still have to update the value
        decsync1.setEntriesForPath(listOf("info"), listOf(Decsync.Entry(datetime3, key, value3)))
        assertEquals(
                mapOf<JsonElement, JsonElement>(
                        JsonPrimitive("name") to value3
                ),
                Decsync.getStaticInfo(dirFactory(), "sync-type", null)
                        .filter { !it.key.jsonPrimitive.content.startsWith("last-active-") }
        )
    }
}