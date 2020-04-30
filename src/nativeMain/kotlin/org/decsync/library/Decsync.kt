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

import kotlinx.cinterop.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.math.min

typealias CArray<T> = CPointer<CPointerVarOf<T>>
typealias CString = CPointer<ByteVar>
typealias CPath = CArray<CString>
typealias V = COpaquePointer

actual sealed class DecsyncException(val errorCode: Int) : Exception()
class InvalidInfoException : DecsyncException(1)
class UnsupportedVersionException : DecsyncException(2)

actual fun getInvalidInfoException(e: Exception): DecsyncException = InvalidInfoException()
actual fun getUnsupportedVersionException(requiredVersion: Int, supportedVersion: Int): DecsyncException = UnsupportedVersionException()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_new")
fun decsync(
        decsync: CArray<V>,
        decsyncDirOrEmpty: String?,
        syncType: String,
        collectionOrEmpty: String?,
        ownAppId: String
): Int {
    val decsyncDir = if (decsyncDirOrEmpty.isNullOrEmpty()) getDefaultDecsyncDir() else decsyncDirOrEmpty
    val nativeDecsyncDir = getNativeFileFromPath(decsyncDir)
    val collection = if (collectionOrEmpty.isNullOrEmpty()) null else collectionOrEmpty
    val localDir = getDecsyncSubdir(nativeDecsyncDir, syncType, collection).child("local", ownAppId)
    return try {
        decsync[0] = StableRef.create<Decsync<V>>(
                Decsync(nativeDecsyncDir, localDir, syncType, collection, ownAppId)
        ).asCPointer()
        0
    } catch (e: DecsyncException) {
        e.errorCode
    }
}

@ExperimentalStdlibApi
@CName(externName = "decsync_so_free")
fun decsyncFree(decsync: V) {
    decsync.asStableRef<Decsync<V>>().dispose()
}

@ExperimentalStdlibApi
@CName(externName = "decsync_so_entry_with_path_new")
fun decsyncEntryWithPath(path: CPath, len: Int, key: String, value: String): V =
        StableRef.create(
                Decsync.EntryWithPath(toPath(path, len), parseJson(key), parseJson(value))
        ).asCPointer()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_entry_with_path_free")
fun decsyncEntryWithPathFree(entryWithPath: V) =
        entryWithPath.asStableRef<Decsync.EntryWithPath>().dispose()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_entry_new")
fun decsyncEntryWithPath(key: String, value: String): V =
        StableRef.create(
                Decsync.Entry(parseJson(key), parseJson(value))
        ).asCPointer()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_entry_free")
fun decsyncEntryFree(entry: V) =
        entry.asStableRef<Decsync.Entry>().dispose()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_stored_entry_new")
fun decsyncStoredEntry(path: CPath, len: Int, key: String): V =
        StableRef.create(
                Decsync.StoredEntry(toPath(path, len), parseJson(key))
        ).asCPointer()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_stored_entry_free")
fun decsyncStoredEntry(storedEntry: V) =
        storedEntry.asStableRef<Decsync.StoredEntry>().dispose()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_add_listener")
fun addListener(decsync: V, subpath: CPath, len: Int, onEntryUpdate: CPointer<CFunction<(CPath, Int, CString, CString, CString, V) -> Unit>>) =
        decsync.asStableRef<Decsync<V>>().get().addListener(toPath(subpath, len)) { path, entry, extra ->
            memScoped {
                val cPath = allocArray<CPointerVarOf<CString>>(path.size)
                for (i in path.indices) {
                    cPath[i] = path[i].cstr.ptr
                }
                val cDatetime = entry.datetime.cstr.ptr
                val cKey = entry.key.toString().cstr.ptr
                val cValue = entry.value.toString().cstr.ptr
                onEntryUpdate(cPath, path.size, cDatetime, cKey, cValue, extra)
            }
        }

@ExperimentalStdlibApi
@CName(externName = "decsync_so_set_entry")
fun setEntry(decsync: V, path: CPath, len: Int, key: String, value: String) =
        decsync.asStableRef<Decsync<V>>().get().setEntry(toPath(path, len), parseJson(key), parseJson(value))

@ExperimentalStdlibApi
@CName(externName = "decsync_so_set_entries")
fun setEntries(decsync: V, entriesWithPath: CArray<V>, len: Int) =
        toList(entriesWithPath, len).map {
            it.asStableRef<Decsync.EntryWithPath>().get()
        }.let {
            decsync.asStableRef<Decsync<V>>().get().setEntries(it)
        }

@ExperimentalStdlibApi
@CName(externName = "decsync_so_set_entries_for_path")
fun setEntriesForPath(decsync: V,
                      path: CPath, len_path: Int,
                      entries: CArray<V>, len_entries: Int
) =
        toList(entries, len_entries).map {
            it.asStableRef<Decsync.Entry>().get()
        }.let {
            decsync.asStableRef<Decsync<V>>().get().setEntriesForPath(toPath(path, len_path), it)
        }

@ExperimentalStdlibApi
@CName(externName = "decsync_so_execute_all_new_entries")
fun executeAllNewEntries(decsync: V, extra: V) =
        decsync.asStableRef<Decsync<V>>().get().executeAllNewEntries(extra)

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_stored_entry")
fun executeStoredEntry(decsync: V,
                       path: CPath, len: Int,
                       key: String,
                       extra: V) =
        decsync.asStableRef<Decsync<V>>().get().executeStoredEntry(
                toPath(path, len),
                parseJson(key),
                extra)

@ExperimentalStdlibApi
@CName(externName = "decsync_so_execute_stored_entries")
fun executeStoredEntries(decsync: V,
                         storedEntries: CArray<V>, len: Int,
                         extra: V) =
        decsync.asStableRef<Decsync<V>>().get().executeStoredEntries(
                toList(storedEntries, len).map {
                    it.asStableRef<Decsync.StoredEntry>().get()
                },
                extra)

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_stored_entries_for_path_exact")
fun executeStoredEntriesForPathExact(decsync: V,
                                     path: CPath, len_path: Int,
                                     extra: V,
                                     keys: CArray<CString>, len_keys: Int) =
        decsync.asStableRef<Decsync<V>>().get().executeStoredEntriesForPathExact(
                toPath(path, len_path),
                extra,
                toList(keys, len_keys).map { parseJson(it.toKString()) })

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_all_stored_entries_for_path_exact")
fun executeAllStoredEntriesForPathExact(decsync: V,
                                        path: CPath, len: Int,
                                        extra: V) =
        decsync.asStableRef<Decsync<V>>().get().executeStoredEntriesForPathExact(
                toPath(path, len),
                extra,
                null)

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_stored_entries_for_path_prefix")
fun executeStoredEntriesForPathPrefix(decsync: V,
                                      path: CPath, len_path: Int,
                                      extra: V,
                                      keys: CArray<CString>, len_keys: Int) =
        decsync.asStableRef<Decsync<V>>().get().executeStoredEntriesForPathPrefix(
                toPath(path, len_path),
                extra,
                toList(keys, len_keys).map { parseJson(it.toKString()) })

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_all_stored_entries_for_path_prefix")
fun executeAllStoredEntriesForPathPrefix(decsync: V,
                                         path: CPath, len: Int,
                                         extra: V) =
        decsync.asStableRef<Decsync<V>>().get().executeStoredEntriesForPathPrefix(
                toPath(path, len),
                extra,
                null)

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_stored_entries_for_path")
fun executeStoredEntriesForPath(decsync: V,
                                path: CPath, len_path: Int,
                                extra: V,
                                keys: CArray<CString>, len_keys: Int) =
        executeStoredEntriesForPathPrefix(decsync, path, len_path, extra, keys, len_keys)

@ExperimentalStdlibApi
@CName (externName = "decsync_so_execute_all_stored_entries_for_path")
fun executeAllStoredEntriesForPath(decsync: V,
                                   path: CPath, len: Int,
                                   extra: V) =
        executeAllStoredEntriesForPathPrefix(decsync, path, len, extra)

@ExperimentalStdlibApi
@CName(externName = "decsync_so_init_stored_entries")
fun initStoredEntries(decsync: V) = decsync.asStableRef<Decsync<V>>().get().initStoredEntries()

@ExperimentalStdlibApi
@CName(externName = "decsync_so_latest_app_id")
fun latestAppId(decsync: V, appId: CString, len: Int) =
        fillBuffer(decsync.asStableRef<Decsync<V>>().get().latestAppId(), appId, len)

@ExperimentalStdlibApi
@CName(externName = "decsync_so_get_static_info")
fun getStaticInfo(decsyncDirOrEmpty: String?, syncType: String, collectionOrEmpty: String?, key: String, value: CString, len: Int) {
    val decsyncDir = if (decsyncDirOrEmpty.isNullOrEmpty()) getDefaultDecsyncDir() else decsyncDirOrEmpty
    val collection = if (collectionOrEmpty.isNullOrEmpty()) null else collectionOrEmpty
    val result = Decsync.getStaticInfo(getNativeFileFromPath(decsyncDir), syncType, collection)
            .getOrElse(parseJson(key), { JsonNull }).toString()
    fillBuffer(result, value, len)
}

@ExperimentalStdlibApi
@CName(externName = "decsync_so_check_decsync_info")
fun checkDecsyncInfoC(decsyncDirOrEmpty: String?): Int {
    val decsyncDir = if (decsyncDirOrEmpty.isNullOrEmpty()) getDefaultDecsyncDir() else decsyncDirOrEmpty
    return try {
        checkDecsyncInfo(getNativeFileFromPath(decsyncDir))
        0
    } catch (e: DecsyncException) {
        e.errorCode
    }
}

@ExperimentalStdlibApi
@CName(externName = "decsync_so_list_decsync_collections")
fun listDecsyncCollectionsC(decsyncDirOrEmpty: String?, syncType: String, collections: CArray<CString>, max_len: Int): Int {
    val decsyncDir = if (decsyncDirOrEmpty.isNullOrEmpty()) getDefaultDecsyncDir() else decsyncDirOrEmpty
    val names = listDecsyncCollections(getNativeFileFromPath(decsyncDir), syncType)
    val len = min(names.size, max_len)
    for (i in 0 until len) {
        fillBuffer(names[i], collections[i]!!, 256)
    }
    return len
}

@CName(externName = "decsync_so_get_app_id")
fun getAppIdC(appName: String, appId: CString, len: Int) =
        fillBuffer(getAppId(appName, null), appId, len)

@CName(externName = "decsync_so_get_app_id_with_id")
fun getAppIdWithIdC(appName: String, id: Int, appId: CString, len: Int) =
        fillBuffer(getAppId(appName, id), appId, len)

private fun <T : CPointer<*>> toList(array: CArray<T>, len: Int): List<T> {
    val list = mutableListOf<T>()
    for (i in 0 until len) {
        list += array[i]!!
    }
    return list
}

private fun toPath(path: CPath, len: Int): List<String> =
        toList(path, len).map { it.toKString() }

private fun fillBuffer(input: String, buffer: CString, buf_len: Int) {
    val array = input.encodeToByteArray()
    val len = min(array.size, buf_len - 1)
    for (i in 0 until len) {
        buffer[i] = array[i]
    }
    buffer[len] = 0
}

private fun parseJson(string: String): JsonElement = json.parseJson(string)