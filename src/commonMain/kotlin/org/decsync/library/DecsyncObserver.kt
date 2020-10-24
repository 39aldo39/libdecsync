package org.decsync.library

import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents an observer that handles the DecSync updates when the internal data of the application
 * changes.
 */
@ExperimentalStdlibApi
abstract class DecsyncObserver(
        private var currentList: List<DecsyncItem>? = null
) {
    /**
     * Function denoting whether DecSync is enabled. When this returns false, no DecSync updates
     * are executed, but the internal list is kept up-to-date.
     */
    abstract fun isDecsyncEnabled(): Boolean

    /**
     * Execute [Decsync.setEntries] for [entries]. Often simply calls a DecSync instance, but
     * sometimes the instance is only available in a callback, making it impossible to require
     * direct access to such instance.
     */
    abstract fun setEntries(entries: List<Decsync.EntryWithPath>)

    /**
     * Similar to [setEntries], but executes [Decsync.executeStoredEntries].
     */
    abstract fun executeStoredEntries(storedEntries: List<Decsync.StoredEntry>)

    /**
     * Writes the current list as DecSync updates. Mostly used during an initial sync.
     */
    fun initSync() {
        applyDiff(insertions = currentList ?: return)
    }

    /**
     * Updates the internal list to the new value [newList]. Any difference between this new state
     * and the previous state are written to the DecSync directory. Furthermore, on insertions the
     * relevant updates are read from the DecSync directory.
     *
     * If this is the first call to [updateList], only the internal list is initialized. No updates
     * are written.
     *
     * @param isFromDecsyncListener denotes whether this calls originates from a DecSync listener.
     * If true, updates are not written, as they are already there.
     */
    fun updateList(newList: List<DecsyncItem>, isFromDecsyncListener: Boolean = false) {
        val newListSorted = newList.sortedWith(compareBy({ it.type }, { it.id }))
        val oldList = currentList
        currentList = newListSorted
        if (oldList == null || !isDecsyncEnabled()) {
            return
        }
        async {
            val diffResult = Diff.calc(oldList, newListSorted, compareBy({ it.type }, { it.id }))
            applyDiff(diffResult, isFromDecsyncListener)
        }
    }

    /**
     * Similar to [updateList], but directly uses a difference.
     */
    fun applyDiff(diffResult: Diff.Result<DecsyncItem>, isFromDecsyncListener: Boolean = false) {
        val (insertions, deletions, changes) = diffResult
        applyDiff(insertions, deletions, changes, isFromDecsyncListener)
    }

    /**
     * Variant of [applyDiff], but with [insertions], [deletions] and [changes] as direct
     * parameters.
     */
    fun applyDiff(
            insertions: List<DecsyncItem> = emptyList(),
            deletions: List<DecsyncItem> = emptyList(),
            changes: List<Pair<DecsyncItem, DecsyncItem>> = emptyList(),
            isFromDecsyncListener: Boolean = false
    ) {
        val entries = mutableListOf<Decsync.EntryWithPath>()
        val storedEntries = mutableListOf<Decsync.StoredEntry>()
        for (item in insertions) {
            for ((storedEntry, value) in item.entries) {
                val (path, key) = storedEntry
                if (value.isDefault()) {
                    storedEntries.add(storedEntry)
                } else {
                    entries.add(Decsync.EntryWithPath(path, key, value.toJson()))
                }
            }
            val (path, key) = item.idStoredEntry ?: continue
            entries.add(Decsync.EntryWithPath(path, key, JsonPrimitive(true)))
        }
        for (item in deletions) {
            val (path, key) = item.idStoredEntry ?: continue
            entries.add(Decsync.EntryWithPath(path, key, JsonPrimitive(false)))
        }
        for ((oldItem, newItem) in changes) {
            for ((storedEntry, value) in newItem.entries) {
                val (path, key) = storedEntry
                if (value != oldItem.entries[storedEntry]) {
                    entries.add(Decsync.EntryWithPath(path, key, value.toJson()))
                }
            }
        }
        if (!isFromDecsyncListener && entries.isNotEmpty()) {
            setEntries(entries)
        }
        if (storedEntries.isNotEmpty()) {
            executeStoredEntries(storedEntries)
        }
    }
}