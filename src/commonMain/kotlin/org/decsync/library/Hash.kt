package org.decsync.library

@ExperimentalStdlibApi
object Hash {
    private const val HASH_BINS = 256
    val allHashes = (0 until HASH_BINS).map { it.toString(16).padStart(2, '0') } + "info"

    fun pathToHash(path: List<String>): String {
        if (path == listOf("info")) return "info"

        val stringHashes = path.map { stringToHash(it) }
        val hash = polyHash(199, stringHashes)
        return hash.toString(16).padStart(2, '0')
    }

    private fun stringToHash(string: String): Int {
        val bytes = string.encodeToByteArray().map { it.toInt() }
        return polyHash(19, bytes)
    }

    private fun polyHash(p: Int, xs: List<Int>): Int {
        var hash = 0
        for (x in xs) {
            hash *= p
            hash += x
            hash %= HASH_BINS
        }
        return hash
    }
}