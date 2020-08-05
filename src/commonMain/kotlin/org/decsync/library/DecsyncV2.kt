/**
 * libdecsync - DecsyncV2.kt
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
internal class DecsyncV2<T>(
        override val decsyncDir: NativeFile,
        override val localDir: DecsyncFile,
        override val syncType: String,
        override val collection: String?,
        override val ownAppId: String
) : DecsyncInst<T>() {
    private val dir = getDecsyncSubdir(decsyncDir, syncType, collection).child("v2")

    private fun entriesWithPathToLines(entriesWithPath: Collection<Decsync.EntryWithPath>): List<String> =
            entriesWithPath.map { it.toJson().toString() }

    private fun getSequences(sequencesFile: DecsyncFile): Map<String, Int> {
        val text = sequencesFile.readText() ?: return emptyMap()
        return try {
            val obj = json.parseJson(text).jsonObject
            obj.mapValues { it.value.int }
        } catch (e: Exception) {
            Log.e(e.message!!)
            emptyMap()
        }
    }

    private fun setSequences(sequencesFile: DecsyncFile, sequences: Map<String, Int>) {
        val obj = sequences.mapValues { JsonLiteral(it.value) }
        val text = JsonObject(obj).toString()
        sequencesFile.writeText(text)
    }

    private fun getLocalSequences(): Map<String, Map<String, Int>> {
        val file = localDir.child("sequences")
        val text = file.readText() ?: return emptyMap()
        return try {
            val obj = json.parseJson(text).jsonObject
            obj.mapValues { (_, subSequences) ->
                subSequences.jsonObject.mapValues { it.value.int }
            }
        } catch (e: Exception) {
            Log.e(e.message!!)
            emptyMap()
        }
    }

    private fun setLocalSequences(sequences: Map<String, Map<String, Int>>) {
        val file = localDir.child("sequences")
        val obj = sequences.mapValues { (_, subSequences) ->
            JsonObject(subSequences.mapValues { JsonLiteral(it.value) })
        }
        val text = JsonObject(obj).toString()
        file.writeText(text)
    }

    override fun setEntry(path: List<String>, key: JsonElement, value: JsonElement) =
            setEntries(listOf(Decsync.EntryWithPath(path, key, value)))

    override fun setEntries(entriesWithPath: List<Decsync.EntryWithPath>) {
        val ownDir = dir.child(ownAppId)
        val sequencesFile = ownDir.child("sequences")
        val sequences = getSequences(sequencesFile).toMutableMap()
        entriesWithPath.groupBy { pathToHash(it.path) }.forEach { (hash, entriesWithPath) ->
            updateEntries(ownDir.child(hash), entriesWithPath.toMutableList())
            sequences[hash] = (sequences[hash] ?: 0) + 1
        }
        setSequences(sequencesFile, sequences)
    }

    override fun setEntriesForPath(path: List<String>, entries: List<Decsync.Entry>) =
            setEntries(entries.map { Decsync.EntryWithPath(path, it) })

    override fun executeAllNewEntries(optExtra: OptExtra<T>) {
        dir.resetCache()
        val appIds = dir.listDirectories().filter { it != ownAppId }
        val ownDir = dir.child(ownAppId)
        val localSequences = getLocalSequences().toMutableMap()
        var updatedSequences = false
        for (appId in appIds) {
            val appDir = dir.child(appId)
            val sequences = getSequences(appDir.child("sequences"))
            for ((hash, sequence) in sequences) {
                if (sequence == localSequences[appId]?.get(hash) ?: 0) continue
                updatedSequences = true

                try {
                    val appFile = appDir.child(hash)
                    val entriesWithPath = appFile.readLines()
                            .mapNotNull { Decsync.EntryWithPath.fromLine(it) }
                            .toMutableList()
                    val ownFile = ownDir.child(hash)
                    updateEntries(ownFile, entriesWithPath)
                    if (optExtra is WithExtra) {
                        executeEntries(entriesWithPath, optExtra.value)
                    }
                    // Do not update the own sequences file,
                    // as other apps will already process the original entries
                } catch (e: Exception) {
                    Log.e(e.message!!)
                }
            }
            localSequences[appId] = sequences
        }
        if (updatedSequences) {
            setLocalSequences(localSequences)
        }
    }

    private fun executeEntries(entriesWithPath: List<Decsync.EntryWithPath>, extra: T) {
        entriesWithPath.groupBy({ it.path }, { it.entry }).forEach { (path, entries) ->
            executeEntriesForPath(path, entries, extra)
        }
    }

    private fun executeEntriesForPath(path: List<String>, entries: List<Decsync.Entry>, extra: T) {
        val listener = listeners.firstOrNull { it.matchesPath(path) }
        if (listener == null) {
            Log.e("Unknown action for path $path")
            return
        }
        listener.onEntriesUpdate(path, entries, extra)
    }

    private fun updateEntries(file: DecsyncFile, entriesWithPath: MutableList<Decsync.EntryWithPath>) {
        data class PathAndKey(val path: List<String>, val key: JsonElement) {
            constructor(entryWithPath: Decsync.EntryWithPath) : this(entryWithPath.path, entryWithPath.entry.key)
        }

        try {
            val storedEntriesWithPath = HashMap<PathAndKey, Decsync.EntryWithPath>()
            file.readLines()
                    .mapNotNull { Decsync.EntryWithPath.fromLine(it) }
                    .forEach {
                        storedEntriesWithPath[PathAndKey(it)] = it
                    }
            var storedEntriesRemoved = false
            val iterator = entriesWithPath.iterator()
            while (iterator.hasNext()) {
                val entryWithPath = iterator.next()
                val storedEntryWithPath = storedEntriesWithPath[PathAndKey(entryWithPath)] ?: continue
                if (entryWithPath.entry.datetime > storedEntryWithPath.entry.datetime) {
                    storedEntriesWithPath.remove(PathAndKey(entryWithPath))
                    storedEntriesRemoved = true
                } else {
                    iterator.remove()
                }
            }
            if (storedEntriesRemoved) {
                val lines = entriesWithPathToLines(storedEntriesWithPath.values)
                file.writeLines(lines)
            }
            val lines = entriesWithPathToLines(entriesWithPath)
            file.writeLines(lines, true)
        } catch (e: Exception) {
            Log.e(e.message ?: "")
        }
    }

    override fun executeStoredEntriesForPathExact(
            path: List<String>,
            extra: T,
            keys: List<JsonElement>?) {
        val hash = pathToHash(path)
        executeStoredEntriesForHash(hash, { it == path }, extra, keys)
    }

    override fun executeStoredEntriesForPathPrefix(
            prefix: List<String>,
            extra: T,
            keys: List<JsonElement>?) {
        val pathPred = { path: List<String> ->
            path.take(prefix.size) == prefix
        }
        for (hash in allHashes) {
            executeStoredEntriesForHash(hash, pathPred, extra, keys)
        }
    }

    private fun executeStoredEntriesForHash(
            hash: String,
            pathPred: (List<String>) -> Boolean,
            extra: T,
            keys: List<JsonElement>?) {
        val file = dir.child(ownAppId, hash)
        val entriesWithPath = file.readLines()
                .mapNotNull { Decsync.EntryWithPath.fromLine(it) }
                .filter { pathPred(it.path) }
                .filter { keys == null || it.entry.key in keys }
        executeEntries(entriesWithPath, extra)
    }

    override fun latestAppId(): String {
        var latestAppId: String? = null
        var latestDatetime: String? = null
        val appIds = dir.listDirectories()
        for (appId in appIds) {
            val appDir = dir.child(appId)
            for (hash in allHashes) {
                val file = appDir.child(hash)
                val datetime = file.readLines()
                        .mapNotNull { Decsync.EntryWithPath.fromLine(it) }
                        .map { it.entry.datetime }
                        .max() ?: continue
                if (latestDatetime == null || datetime > latestDatetime ||
                        appId == ownAppId && datetime == latestDatetime) {
                    latestAppId = appId
                    latestDatetime = datetime
                }
            }
        }
        return latestAppId ?: ownAppId
    }

    override fun deleteOwnEntries() {
        deleteOwnSubdir(dir)
    }

    companion object {
        private const val HASH_BINS = 256
        private fun pathToHash(path: List<String>): String {
            if (path == listOf("info")) return "info"

            var hash = 0
            for (string in path) {
                hash *= 119
                hash += stringToHash(string)
                hash %= HASH_BINS
            }
            return hash.toString(16).padStart(2, '0')
        }
        private fun stringToHash(string: String): Int {
            var hash = 0
            for (byte in string.encodeToByteArray()) {
                hash *= 19
                hash += byte
                hash %= HASH_BINS
            }
            return hash
        }
        private val allHashes = (0 until HASH_BINS).map { it.toString(16) } + "info"

        fun getStaticInfo(
                decsyncDir: NativeFile,
                syncType: String,
                collection: String?,
                info: MutableMap<JsonElement, JsonElement>,
                datetimes: MutableMap<JsonElement, String>
        ) {
            val hash = pathToHash(listOf("info"))
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection).child("v2")
            val appIds = dir.listDirectories()
            for (appId in appIds) {
                dir.child(appId, hash).readLines()
                        .mapNotNull { Decsync.EntryWithPath.fromLine(it) }
                        .filter { it.path == listOf("info") }
                        .forEach { entryWithPath ->
                            val entry = entryWithPath.entry
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
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection).child("v2")
            return dir.listDirectories()
        }

        fun deleteApp(
                decsyncDir: NativeFile,
                syncType: String,
                collection: String?,
                appId: String
        ) {
            val dir = getDecsyncSubdir(decsyncDir, syncType, collection).child("v2")
            deleteSubdir(dir, appId)
        }
    }
}
