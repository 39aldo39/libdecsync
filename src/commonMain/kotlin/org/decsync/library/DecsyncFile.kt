/**
 * libdecsync - DecsyncFile.kt
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

/**
 * This class adds the following abstractions to a [NativeFile]:
 *   - Work with URL-decoded names, hence the name is unrestricted.
 *   - Add hidden files, whose names start with a period.
 *   - View the file content as a list of non-blank strings separated by newlines.
 *     The lines themselves cannot contain newlines.
 *   - Add the methods [listFilesRecursiveRelative] and [listDirectories].
 */
@ExperimentalStdlibApi
class DecsyncFile(val file: NativeFile) {

    fun child(name: String): DecsyncFile {
        val encodedName = Url.encode(name)
        val file = this.file.child(encodedName)
        return DecsyncFile(file)
    }

    fun hiddenChild(name: String): DecsyncFile {
        val encodedName = Url.encode(name)
        val file = this.file.child(".$encodedName")
        return DecsyncFile(file)
    }

    fun child(names: List<String>): DecsyncFile {
        var result = this
        for (name in names) {
            result = result.child(name)
        }
        return result
    }

    fun child(vararg names: String): DecsyncFile = child(names.asList())

    fun readLines(readBytes: Int = 0): List<String> {
        val bytes = file.read(readBytes) ?: return emptyList()
        return byteArrayToString(bytes)
                .split('\n')
                .filter { it.isNotBlank() }
    }

    fun writeLines(lines: List<String>, append: Boolean = false) {
        val linesNotBlank = lines.filter { it.isNotBlank() }
        val builder = StringBuilder()
        for (line in linesNotBlank) {
            builder.append(line)
            builder.append('\n')
        }
        val text = builder.toString().encodeToByteArray()
        file.write(text, append)
    }

    fun readText(): String? {
        val lines = readLines()
        return when (lines.size) {
            0 -> null
            1 -> lines[0]
            else -> throw Exception("Multiple lines read as text: $lines")
        }
    }

    fun writeText(text: String) = writeLines(listOf(text))

    fun length(): Int = file.length()

    fun delete() = file.deleteRecursive()

    // Invalidates all child instances
    fun resetCache() = file.resetCache()

    override fun toString(): String = file.toString()

    fun mkdir() = file.mkdir().let { Unit }

    fun listFilesRecursiveRelative(readBytesSrc: DecsyncFile? = null,
                                   pathPred: (List<String>) -> Boolean = { true }): List<ArrayList<String>> {
        return when (val node = file.fileSystemNode) {
            is RealFile -> listOf(arrayListOf())
            is RealDirectory -> {
                // Skip equal sequence numbers
                if (readBytesSrc != null) {
                    val seqFile = hiddenChild("decsync-sequence")
                    val seq = seqFile.readText()
                    val readBytesSeqFile = readBytesSrc.hiddenChild("decsync-sequence")
                    val readBytesSeq = readBytesSeqFile.readText()
                    if (seq != null) {
                        if (seq == readBytesSeq) {
                            return emptyList()
                        } else {
                            readBytesSeqFile.writeText(seq)
                        }
                    }
                }

                node.children(file)
                        .map { it.name }
                        .filter { it[0] != '.' }
                        .mapNotNull { encodedName -> Url.decode(encodedName) }
                        .filter { name -> pathPred(listOf(name)) }
                        .flatMap { name ->
                            val newReadBytesSrc = readBytesSrc?.child(name)
                            val newPred = { path: List<String> -> pathPred(listOf(name) + path) }
                            val paths = child(name).listFilesRecursiveRelative(newReadBytesSrc, newPred)
                            paths.forEach { it.add(0, name) }
                            paths
                        }
            }
            is NonExistingNode -> emptyList()
        }
    }

    fun listDirectories(): List<String> {
        return file.children()
                .filter { it.name[0] != '.' && it.fileSystemNode is RealDirectory }
                .mapNotNull { Url.decode(it.name) }
    }
}
