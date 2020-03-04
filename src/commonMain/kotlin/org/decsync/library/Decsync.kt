/**
 * libdecsync - Decsync.kt
 *
 * Copyright (C) 2019 Aldo Gunsing
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package org.decsync.library

import kotlinx.io.IOException
import kotlinx.serialization.json.*
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
val json = Json(JsonConfiguration.Stable)

const val SUPPORTED_VERSION = 1

expect sealed class DecsyncException : Exception
expect fun getInvalidInfoException(e: Exception): DecsyncException
expect fun getUnsupportedVersionException(requiredVersion: Int, supportedVersion: Int): DecsyncException

/**
 * The `DecSync` class represents an interface to synchronized key-value mappings stored on the file
 * system.
 *
 * The mappings can be synchronized by synchronizing the directory [decsyncDir]. The stored mappings
 * are stored in a conflict-free way. When the same keys are updated independently, the most recent
 * value is taken. This should not cause problems when the individual values contain as little
 * information as possible.
 *
 * Every entry consists of a path, a key and a value. The path is a list of strings which contains
 * the location to the used mapping. This can make interacting with the data easier. It is also used
 * to construct a path in the file system. All characters are allowed in the path. However, other
 * limitations of the file system may apply. For example, there may be a maximum length or the file
 * system may be case insensitive.
 *
 * To update an entry, use the method [setEntry]. When multiple keys in the same path are updated
 * simultaneous, it is encouraged to use the more efficient methods [setEntriesForPath] and
 * [setEntries].
 *
 * To get notified about updated entries, use the method [executeAllNewEntries] to get all updated
 * entries and call the corresponding listeners. Listeners can be added by the method [addListener].
 *
 * Sometimes, updates cannot be execute immediately. For example, if the name of a category is
 * updated when the category does not exist yet, the name cannot be changed. In such cases, the
 * updates have to be executed retroactively. In the example, the update can be executed when the
 * category is created. For such cases, use the method [executeStoredEntry], [executeStoredEntries]
 * or [executeStoredEntriesForPath].
 *
 * Finally, to initialize the stored entries to the most recent values, use the method
 * [initStoredEntries]. This method is almost exclusively used when the application is installed. It
 * is almost always followed by a call to [executeStoredEntry] or similar.
 *
 * @param T the type of the extra data passed to the [listeners].
 * @param decsyncDir the directory in which the synchronized DecSync files are stored. For the
 * default location, use [getDefaultDecsyncDir].
 * @param syncType the type of data to sync. For example, "rss", "contacts" or "calendars".
 * @param collection an optional collection identifier when multiple instances of the [syncType] are
 * supported. For example, this is the case for "contacts" and "calendars", but not for "rss".
 * @property ownAppId the unique appId corresponding to the stored data by the application. There
 * must not be two simultaneous instances with the same appId. However, if an application is
 * reinstalled, it may reuse its old appId. In that case, it has to call [initStoredEntries] and
 * [executeStoredEntry] or similar. Even if the old appId is not reused, it is still recommended to
 * call these. For the default appId, use [getAppId].
 * @throws DecsyncException if a DecSync configuration error occurred.
 */
@ExperimentalStdlibApi
class Decsync<T> internal constructor(
        decsyncDir: NativeFile,
        syncType: String,
        collection: String?,
        private val ownAppId: String
) {
    private val dir = getDecsyncSubdir(decsyncDir, syncType, collection)
    private val listeners: MutableList<OnEntryUpdateListener<T>> = mutableListOf()
    private var isInInit = false

    init {
        checkDecsyncInfo(decsyncDir)
    }

    /**
     * Adds a listener, which describes the actions to execute on some updated entries. When an
     * entry is updated, the function [onEntryUpdate] is called on the listener whose [subpath]
     * matches. It matches when the given subpath is a prefix of the path of the entry.
     */
    fun addListener(subpath: List<String>, onEntryUpdate: (path: List<String>, entry: Entry, extra: T) -> Unit) {
        listeners += OnEntryUpdateListener(subpath, onEntryUpdate)
    }

    private class OnEntryUpdateListener<T>(
            private val subpath: List<String>,
            private val onEntryUpdate: (path: List<String>, entry: Entry, extra: T) -> Unit
    ) {
        fun matchesPath(path: List<String>): Boolean = path.take(subpath.size) == subpath
        fun onEntriesUpdate(path: List<String>, entries: List<Entry>, extra: T) {
            val convertedPath = path.drop(subpath.size)
            for (entry in entries) {
                onEntryUpdate(convertedPath, entry, extra)
            }
        }
    }

    /**
     * Represents an [Entry] with its path.
     */
    class EntryWithPath(val path: List<String>, val entry: Entry) {
        /**
         * Convenience constructor for nicer syntax.
         */
        constructor(path: List<String>, key: JsonElement, value: JsonElement) : this(path, Entry(key, value))
    }

    /**
     * Represents a key/value pair stored by DecSync. Additionally, it has a datetime property
     * indicating the most recent update. It does not store its path, see [EntryWithPath].
     */
    class Entry(val datetime: String, val key: JsonElement, val value: JsonElement) {
        /**
         * Convenience constructor which sets the [datetime] property to the current datetime.
         */
        constructor(key: JsonElement, value: JsonElement) : this(currentDatetime(), key, value)

        internal fun toJson(): JsonElement {
            val array = listOf(JsonLiteral(datetime), key, value)
            return JsonArray(array)
        }

        override fun toString(): String = toJson().toString()

        companion object {
            internal fun fromLine(line: String): List<Entry> {
                try {
                    val array = json.parseJson(line).jsonArray
                    if (array.size == 3 && array[0] is JsonPrimitive) {
                        return listOf(fromArray(array))
                    }
                    return array.map { fromArray(it.jsonArray) }
                } catch (e: JsonException) {
                    Log.w("Invalid entry: $line")
                    Log.w(e.message!!)
                    return emptyList()
                }
            }

            private fun fromArray(array: JsonArray): Entry {
                val datetime = array[0].content
                val key = array[1]
                val value = array[2]
                return Entry(datetime, key, value)
            }
        }
    }

    private fun entriesToLines(entries: Iterable<Entry>): List<String> =
            entries.map { it.toJson().toString() }

    /**
     * Represents the path and key stored by DecSync. It does not store its value, as it is unknown
     * when retrieving a stored entry.
     */
    class StoredEntry(val path: List<String>, val key: JsonElement)

    private class EntriesLocation(val path: List<String>, val newEntriesFile: DecsyncFile, val storedEntriesFile: DecsyncFile?, val readBytesFile: DecsyncFile?)

    private fun getNewEntriesLocation(path: List<String>, appId: String): EntriesLocation =
            EntriesLocation(
                    path,
                    dir.child(listOf("new-entries", appId) + path),
                    dir.child(listOf("stored-entries", ownAppId) + path),
                    dir.child(listOf("read-bytes", ownAppId, appId) + path)
            )

    private fun getStoredEntriesLocation(path: List<String>): EntriesLocation =
            EntriesLocation(
                    path,
                    dir.child(listOf("stored-entries", ownAppId) + path),
                    null,
                    null
            )

    /**
     * Associates the given [value] with the given [key] in the map corresponding to the given
     * [path]. This update is sent to synchronized devices.
     */
    fun setEntry(path: List<String>, key: JsonElement, value: JsonElement) =
            setEntriesForPath(path, listOf(Entry(key, value)))

    /**
     * Like [setEntry], but allows multiple entries to be set. This is more efficient if multiple
     * entries share the same path.
     *
     * @param entriesWithPath entries with path which are inserted.
     */
    fun setEntries(entriesWithPath: List<EntryWithPath>) =
        entriesWithPath.groupBy({ it.path }, { it.entry }).forEach { (path, entries) ->
            setEntriesForPath(path, entries)
        }

    /**
     * Like [setEntries], but only allows the entries to have the same path. Consequently, it can
     * be slightly more convenient since the path has to be specified just once.
     *
     * @param path path to the map in which the entries are inserted.
     * @param entries entries which are inserted.
     */
    fun setEntriesForPath(path: List<String>, entries: List<Entry>) {
        Log.d("Write to path $path")
        val entriesLocation = getNewEntriesLocation(path, ownAppId)

        // Write new entries
        val lines = entriesToLines(entries)
        entriesLocation.newEntriesFile.writeLines(lines, true)

        // Update sequence files
        var sequenceDir = dir.child("new-entries", ownAppId)
        for (name in path) {
            val file = sequenceDir.hiddenChild("decsync-sequence")
            val version = file.readText()?.toLongOrNull() ?: 0
            file.writeText((version + 1).toString())
            sequenceDir = sequenceDir.child(name)
        }

        // Update stored entries
        updateStoredEntries(entriesLocation, entries.toMutableList())
    }

    /**
     * Gets all updated entries and executes the corresponding actions.
     *
     * @param extra extra userdata passed to the [listeners].
     */
    fun executeAllNewEntries(extra: T) {
        if (isInInit) {
            Log.d("executeAllNewEntries called while in init")
            return
        }
        Log.d("Execute all new entries in $dir")
        val newEntriesDir = dir.child("new-entries")
        newEntriesDir.resetCache()
        val readBytesDir = dir.child("read-bytes", ownAppId)
        val appIds = newEntriesDir.listDirectories().filter { it != ownAppId }
        for (appId in appIds) {
            newEntriesDir.child(appId)
                    .listFilesRecursiveRelative(readBytesDir.child(appId))
                    .map { getNewEntriesLocation(it, appId) }
                    .forEach { executeEntriesLocation(it, extra) }
        }
    }

    private fun executeEntriesLocation(entriesLocation: EntriesLocation,
                                       extra: T,
                                       keys: List<JsonElement>? = null) {
        val readBytes = entriesLocation.readBytesFile?.readText()?.toIntOrNull() ?: 0
        val size = entriesLocation.newEntriesFile.length()
        if (readBytes >= size) return
        entriesLocation.readBytesFile?.writeText(size.toString())

        Log.d("Execute entries of ${entriesLocation.path}")
        val entries = entriesLocation.newEntriesFile
                .readLines(readBytes)
                .flatMap { Entry.fromLine(it) }
                .filter { keys == null || it.key in keys }
                .groupBy { it.key }.values
                .map { it.maxBy { it.datetime }!! }
                .toMutableList()
        executeEntries(entriesLocation, entries, extra)
    }

    private fun executeEntries(entriesLocation: EntriesLocation, entries: MutableList<Entry>, extra: T) {
        updateStoredEntries(entriesLocation, entries)

        val listener = listeners.firstOrNull { it.matchesPath(entriesLocation.path) }
        if (listener == null) {
            Log.e("Unknown action for path ${entriesLocation.path}")
            return
        }
        listener.onEntriesUpdate(entriesLocation.path, entries, extra)
    }

    private fun updateStoredEntries(entriesLocation: EntriesLocation, entries: MutableList<Entry>) {
        if (entriesLocation.storedEntriesFile == null) {
            return
        }

        try {
            val storedEntries = HashMap<JsonElement, Entry>()
            entriesLocation.storedEntriesFile.readLines()
                    .flatMap { Entry.fromLine(it) }
                    .forEach {
                        storedEntries[it.key] = it
                    }
            var storedEntriesRemoved = false
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val storedEntry = storedEntries[entry.key] ?: continue
                if (entry.datetime > storedEntry.datetime) {
                    storedEntries.remove(entry.key)
                    storedEntriesRemoved = true
                } else {
                    iterator.remove()
                }
            }
            if (storedEntriesRemoved) {
                val lines = entriesToLines(storedEntries.values)
                entriesLocation.storedEntriesFile.writeLines(lines)
            }
            val lines = entriesToLines(entries)
            entriesLocation.storedEntriesFile.writeLines(lines, true)

            updateLatestStoredEntry(entries)
        } catch (e: Exception) {
            Log.e(e.message ?: "")
        }
    }

    private fun updateLatestStoredEntry(entries: List<Entry>) {
        val maxDatetime = entries.map { it.datetime }.max() ?: return
        val latestStoredEntryFile = dir.child("info", ownAppId, "latest-stored-entry")
        val latestDatetime = latestStoredEntryFile.readText()
        if (latestDatetime == null || maxDatetime > latestDatetime) {
            latestStoredEntryFile.writeText(maxDatetime)
        }
    }

    /**
     * Gets the stored entry in [path] with key [key] and executes the corresponding action, passing
     * extra data [extra] to the listener.
     */
    fun executeStoredEntry(path: List<String>, key: JsonElement, extra: T) =
            executeStoredEntriesForPath(path, extra, listOf(key))

    /**
     * Like [executeStoredEntry], but allows multiple entries to be executed. This is more efficient
     * if multiple entries share the same path.
     *
     * @param storedEntries entries with path and key to be executed.
     * @param extra extra data passed to the listeners.
     */
    fun executeStoredEntries(storedEntries: List<StoredEntry>, extra: T) =
        storedEntries.groupBy({ it.path }, { it.key }).forEach { (path, keys) ->
            executeStoredEntriesForPath(path, extra, keys)
        }

    /**
     * Like [executeStoredEntries], but only allows the stored entries to have the same path.
     * Consequently, it can be slightly more convenient since the path has to be specified just
     * once.
     *
     * @param path path to the entries to execute.
     * @param extra extra data passed to the [listeners].
     * @param keys list of keys to execute. When null, all keys are executed.
     */
    fun executeStoredEntriesForPath(path: List<String>,
                                    extra: T,
                                    keys: List<JsonElement>? = null) {
        Log.d("Execute stored entries of $path")
        dir.child(listOf("stored-entries", ownAppId) + path)
                .listFilesRecursiveRelative()
                .map { getStoredEntriesLocation(path + it) }
                .forEach { entriesLocation ->
                    executeEntriesLocation(entriesLocation, extra, keys)
                }
    }

    /**
     * Initializes the stored entries. This method does not execute any actions. This is often
     * followed with a call to [executeStoredEntries].
     */
    fun initStoredEntries() {
        // Get the most up-to-date appId
        val otherAppId = latestAppId()

        // Copy the stored files and update the read bytes
        if (otherAppId != ownAppId) {
            isInInit = true

            val ownStoredEntriesDir = dir.child("stored-entries", ownAppId)
            val otherStoredEntriesDir = dir.child("stored-entries", otherAppId)
            ownStoredEntriesDir.delete()
            otherStoredEntriesDir.copy(ownStoredEntriesDir)

            val ownReadBytesDir = dir.child("read-bytes", ownAppId)
            val otherReadBytesDir = dir.child("read-bytes", otherAppId)
            ownReadBytesDir.delete()
            otherReadBytesDir.copy(ownReadBytesDir)

            isInInit = false
        }
    }

    /**
     * Returns the most up-to-date appId. This is the appId which has stored the most recent entry.
     * In case of a tie, the appId corresponding to the current application is used, if possible.
     */
    fun latestAppId(): String {
        var latestAppId: String? = null
        var latestDatetime: String? = null
        val infoDir = dir.child("info")
        val appIds = infoDir.listDirectories()
        for (appId in appIds) {
            val file = infoDir.child(appId, "latest-stored-entry")
            val datetime = file.readText() ?: continue
            if (latestDatetime == null || datetime > latestDatetime ||
                    appId == ownAppId && datetime == latestDatetime)
            {
                latestAppId = appId
                latestDatetime = datetime
            }
        }
        return latestAppId ?: ownAppId
    }

    companion object {
        /**
         * Returns the most up-to-date values stored in the path `["info"]`, in the given DecSync
         * dir [decsyncDir], sync type [syncType] and collection [collection].
         */
        internal fun getStaticInfo(decsyncDir: NativeFile, syncType: String, collection: String?): Map<JsonElement, JsonElement> {
            Log.d("Get static info in $decsyncDir for syncType $syncType and collection $collection")
            val result = hashMapOf<JsonElement, JsonElement>()
            val datetimes = hashMapOf<JsonElement, String>()
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection)
            val storedEntriesDir = dir.child("stored-entries")
            val appIds = storedEntriesDir.listDirectories()
            for (appId in appIds) {
                storedEntriesDir.child(appId, "info")
                        .readLines()
                        .flatMap { Entry.fromLine(it) }
                        .forEach { entry ->
                            val oldDatetime = datetimes[entry.key]
                            if (oldDatetime == null || entry.datetime > oldDatetime) {
                                result[entry.key] = entry.value
                                datetimes[entry.key] = entry.datetime
                            }
                        }
            }
            return result
        }
    }
}

/**
 * Checks whether the .decsync-info file in [decsyncDir] is of the right format and contains a
 * supported version. If it does not exist, a new one with version 1 is created.
 *
 * @throws DecsyncException if a DecSync configuration error occurred.
 */
@ExperimentalStdlibApi
internal fun checkDecsyncInfo(decsyncDir: NativeFile) {
    try {
        val obj = getDecsyncInfo(decsyncDir) ?: defaultDecsyncInfo.also { setDecsyncInfo(decsyncDir, it) }
        val decsyncVersion = obj.getPrimitive("version").int
        if (decsyncVersion !in 1..SUPPORTED_VERSION) {
            throw getUnsupportedVersionException(decsyncVersion, SUPPORTED_VERSION)
        }
    } catch (e: JsonException) {
        throw getInvalidInfoException(e)
    }
}

@ExperimentalStdlibApi
private fun getDecsyncSubdir(decsyncDir: NativeFile, syncType: String, collection: String?): DecsyncFile {
    var dir = DecsyncFile(decsyncDir)
    dir = dir.child(syncType)
    if (collection != null) {
        dir = dir.child(collection)
    }
    return dir
}

private val defaultDecsyncInfo: JsonObject = JsonObject(mapOf("version" to JsonLiteral(1)))

@ExperimentalStdlibApi
private fun getDecsyncInfo(decsyncDir: NativeFile): JsonObject? {
    val file = decsyncDir.child(".decsync-info")
    return when (file) {
        is RealFile -> {
            val text = byteArrayToString(file.read())
            json.parseJson(text).jsonObject
        }
        is RealDirectory -> throw IOException("getDecsyncInfo called on directory $file")
        is NonExistingFile -> null
    }
}

@ExperimentalStdlibApi
private fun setDecsyncInfo(decsyncDir: NativeFile, obj: JsonObject) {
    val file = decsyncDir.child(".decsync-info")
    val fileReal = when (file) {
        is RealFile -> file
        is RealDirectory -> throw IOException(".decsync-info is a directory")
        is NonExistingFile -> file.mkfile()
    }
    fileReal.write(obj.toString().encodeToByteArray())
}

/**
 * Returns a list of DecSync collections inside a [decsyncDir] for a [syncType]. This function does
 * not apply for sync types with single instances.
 *
 * @param decsyncDir the path to the main DecSync directory.
 * @param syncType the type of data to sync. For example, "contacts" or "calendars".
 */
@ExperimentalStdlibApi
internal fun listDecsyncCollections(decsyncDir: NativeFile, syncType: String): List<String> =
        getDecsyncSubdir(decsyncDir, syncType, null).listDirectories()

/**
 * Returns the appId of the current device and application combination.
 *
 * @param appName the name of the application.
 * @param id an optional integer (between 0 and 100000 exclusive) to distinguish different instances
 * on the same device and application.
 */
fun getAppId(appName: String, id: Int? = null): String {
    val appId = "${getDeviceName()}-$appName"
    return when (id) {
        null -> appId
        else -> "$appId-${id.toString().padStart(5, '0')}"
    }
}