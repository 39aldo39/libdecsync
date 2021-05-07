/**
 * libdecsync - DecsyncV1.kt
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

import kotlinx.serialization.json.*

@ExperimentalStdlibApi
internal class DecsyncV1<T>(
        override val decsyncDir: NativeFile,
        override val localDir: DecsyncFile,
        override val syncType: String,
        override val collection: String?,
        override val ownAppId: String
) : DecsyncInst<T>() {
    private val dir = getDecsyncSubdir(decsyncDir, syncType, collection)

    init {
        // Create shared directories
        dir.child("info").mkdir()
        dir.child("new-entries").mkdir()
        dir.child("read-bytes").mkdir()
        dir.child("stored-entries").mkdir()
    }

    private fun entriesToLines(entries: Collection<Decsync.Entry>): List<String> =
            entries.map { it.toJson().toString() }

    private class EntriesLocation(val path: List<String>, val newEntriesFile: DecsyncFile, val storedEntriesFile: DecsyncFile, val readBytesFile: DecsyncFile)

    private fun getNewEntriesLocation(path: List<String>, appId: String): EntriesLocation =
            EntriesLocation(
                    path,
                    dir.child(listOf("new-entries", appId) + path),
                    dir.child(listOf("stored-entries", ownAppId) + path),
                    dir.child(listOf("read-bytes", ownAppId, appId) + path)
            )

    override fun setEntriesForPath(path: List<String>, entries: List<Decsync.Entry>) {
        val entriesLocation = getNewEntriesLocation(path, ownAppId)

        // Update stored entries
        val entries = entries.toMutableList()
        updateStoredEntries(entriesLocation, entries, NoExtra(), true)
        if (entries.isEmpty()) return

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
    }

    override fun executeAllNewEntries(optExtra: OptExtra<T>) {
        val newEntriesDir = dir.child("new-entries")
        newEntriesDir.resetCache()
        val readBytesDir = dir.child("read-bytes", ownAppId)
        val appIds = newEntriesDir.listDirectories().filter { it != ownAppId }
        for (appId in appIds) {
            newEntriesDir.child(appId)
                    .listFilesRecursiveRelative(readBytesDir.child(appId)) { path ->
                        val entriesLocation = getNewEntriesLocation(path, appId)
                        executeEntriesLocation(entriesLocation, optExtra)
                    }
        }
    }

    private fun executeEntriesLocation(entriesLocation: EntriesLocation,
                                       optExtra: OptExtra<T>,
                                       keys: List<JsonElement>? = null): Boolean {
        val readBytes = entriesLocation.readBytesFile.readText()?.toIntOrNull() ?: 0
        val size = entriesLocation.newEntriesFile.length()
        if (readBytes >= size) return true

        val entries = readEntriesFromFile(entriesLocation.newEntriesFile, readBytes, keys)
        val allSuccess = updateStoredEntries(entriesLocation, entries, optExtra)

        if (allSuccess) {
            entriesLocation.readBytesFile.writeText(size.toString())
        }
        return allSuccess
    }

    private fun readEntriesFromFile(file: DecsyncFile, readBytes: Int, keys: List<JsonElement>? = null): MutableList<Decsync.Entry> {
        return file.readLines(readBytes)
                .mapNotNull { Decsync.Entry.fromLine(it) }
                .filter { keys == null || it.key in keys }
                .groupBy { it.key }.values
                .map { it.maxByOrNull { it.datetime }!! }
                .toMutableList()
    }

    private fun updateStoredEntries(
            entriesLocation: EntriesLocation,
            entries: MutableList<Decsync.Entry>,
            optExtra: OptExtra<T>,
            requireNewValue: Boolean = false
    ): Boolean {
        // Get a map of the stored entries
        val storedEntries = HashMap<JsonElement, Decsync.Entry>()
        entriesLocation.storedEntriesFile.readLines()
                .mapNotNull { Decsync.Entry.fromLine(it) }
                .forEach {
                    storedEntries[it.key] = it
                }

        // Filter out entries which are not newer than the stored one
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val storedEntry = storedEntries[entry.key] ?: continue
            if (entry.datetime <= storedEntry.datetime || (requireNewValue && entry.value == storedEntry.value)) {
                iterator.remove()
            }
        }

        // Execute the new entries
        // This also filters out any entries for which the listener fails
        val success = if (optExtra is WithExtra) {
            callListener(entriesLocation.path, entries, optExtra.value)
        } else {
            true
        }

        // Filter out the stored entries for which a new value is inserted
        var storedEntriesRemoved = false
        for (entry in entries) {
            val storedEntry = storedEntries.remove(entry.key)
            if (storedEntry != null) {
                storedEntriesRemoved = true
            }
        }

        // Write the new stored entries
        if (storedEntriesRemoved) {
            val lines = entriesToLines(storedEntries.values)
            entriesLocation.storedEntriesFile.writeLines(lines)
        }
        val lines = entriesToLines(entries)
        entriesLocation.storedEntriesFile.writeLines(lines, true)

        updateLatestStoredEntry(entries)

        return success
    }

    private fun updateLatestStoredEntry(entries: List<Decsync.Entry>) {
        val maxDatetime = entries.map { it.datetime }.maxOrNull() ?: return
        val latestStoredEntryFile = dir.child("info", ownAppId, "latest-stored-entry")
        val latestDatetime = latestStoredEntryFile.readText()
        if (latestDatetime == null || maxDatetime > latestDatetime) {
            latestStoredEntryFile.writeText(maxDatetime)
        }
    }

    override fun executeStoredEntriesForPathExact(path: List<String>, extra: T, keys: List<JsonElement>?): Boolean =
            executeStoredEntriesForPathPrefix(path, extra, keys)

    override fun executeStoredEntriesForPathPrefix(
            prefix: List<String>,
            extra: T,
            keys: List<JsonElement>?
    ): Boolean {
        return dir.child(listOf("stored-entries", ownAppId) + prefix)
                .listFilesRecursiveRelative {
                    val path = prefix + it
                    val file = dir.child(listOf("stored-entries", ownAppId) + path)
                    val entries = readEntriesFromFile(file, 0, keys)
                    callListener(path, entries, extra)
                }
    }

    override fun latestAppId(): String {
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

    override fun deleteOwnEntries() {
        deleteOwnSubdir(dir.child("info"))
        deleteOwnSubdir(dir.child("new-entries"))
        deleteOwnSubdir(dir.child("read-bytes"))
        deleteOwnSubdir(dir.child("stored-entries"))
    }

    companion object {
        fun getStaticInfo(
                decsyncDir: NativeFile,
                syncType: String,
                collection: String?,
                info: MutableMap<JsonElement, JsonElement>,
                datetimes: MutableMap<JsonElement, String>
        ) {
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection)
            val storedEntriesDir = dir.child("stored-entries")
            val appIds = storedEntriesDir.listDirectories()
            for (appId in appIds) {
                storedEntriesDir.child(appId, "info")
                        .readLines()
                        .mapNotNull { Decsync.Entry.fromLine(it) }
                        .forEach { entry ->
                            val oldDatetime = datetimes[entry.key]
                            if (oldDatetime == null || entry.datetime > oldDatetime) {
                                info[entry.key] = entry.value
                                datetimes[entry.key] = entry.datetime
                            }
                        }
            }
        }

        fun getStaticInfo(
                decsyncDir: NativeFile,
                syncType: String,
                collection: String?
        ): Map<JsonElement, JsonElement> {
            val info = mutableMapOf<JsonElement, JsonElement>()
            val datetimes = mutableMapOf<JsonElement, String>()
            getStaticInfo(decsyncDir, syncType, collection, info, datetimes)
            return info
        }

        fun getActiveApps(
                decsyncDir: NativeFile,
                syncType: String,
                collection: String?
        ): List<String> {
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection)
            val storedEntriesDir = dir.child("stored-entries")
            return storedEntriesDir.listDirectories()
        }

        fun deleteApp(
                decsyncDir: NativeFile,
                syncType: String,
                collection: String?,
                appId: String,
                includeNewEntries: Boolean
        ) {
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection)
            deleteSubdir(dir.child("info"), appId)
            if (includeNewEntries) {
                deleteSubdir(dir.child("new-entries"), appId)
            }
            deleteSubdir(dir.child("read-bytes"), appId)
            deleteSubdir(dir.child("stored-entries"), appId)
        }
    }
}