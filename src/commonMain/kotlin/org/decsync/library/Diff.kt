package org.decsync.library

object Diff {
    data class Result<out T>(
            val insertions: List<T>,
            val deletions: List<T>,
            val changes: List<Pair<T, T>>
    )

    /**
     * Calculates the difference between two lists [oldList] and [newList]. The result consists of:
     * - [Result.insertions]: values present in [newList] but not in [oldList],
     * - [Result.deletions]: values present in [oldList] but not in [newList],
     * - [Result.changes]: distinct values with the same identifier present in both [oldList] and
     *   [newList], given as a pair with the old and new value.
     * The values are identified by [comparator], and both [oldList] and [newList] have to be sorted
     * with respect to this ordering.
     */
    fun <T> calc(oldList: List<T>, newList: List<T>, comparator: Comparator<T>): Result<T> {
        var i = 0
        var j = 0
        val insertions = mutableListOf<T>()
        val deletions = mutableListOf<T>()
        val changes = mutableListOf<Pair<T, T>>()
        while (true) {
            if (i == oldList.size) {
                insertions += newList.drop(j)
                break
            }
            if (j == newList.size) {
                deletions += oldList.drop(i)
                break
            }
            val cmp = comparator.compare(oldList[i], newList[j])
            when {
                cmp > 0 -> {
                    insertions += newList[j]
                    j++
                }
                cmp < 0 -> {
                    deletions += oldList[i]
                    i++
                }
                else -> {
                    if (oldList[i] != newList[j]) {
                        changes += oldList[i] to newList[j]
                    }
                    i++
                    j++
                }
            }
        }
        return Result(insertions, deletions, changes)
    }
}