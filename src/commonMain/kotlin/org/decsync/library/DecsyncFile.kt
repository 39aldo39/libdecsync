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

@ExperimentalStdlibApi
class DecsyncFile(
        var file: NativeFile
) {
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
        return when (val file = file) {
            is RealFile -> {
                val bytes = file.read(readBytes)
                // There should never be an empty file
                // It probably means that an (uncaught) error occurred
                if (readBytes == 0 && bytes.isEmpty()) {
                    throw Exception("Read empty file: $file")
                }
                byteArrayToString(bytes)
                        .split('\n')
                        .filter { it.isNotBlank() }
            }
            is RealDirectory -> throw Exception("readLines called on directory $file")
            is NonExistingFile -> emptyList()
        }
    }

    fun writeLines(lines: List<String>, append: Boolean = false) {
        val linesNotBlank = lines.filter { it.isNotBlank() }
        // Make sure we do not create empty files
        if (linesNotBlank.isEmpty()) {
            if (!append) {
                delete()
            }
            return
        }
        val fileReal = when (val file = file) {
            is RealFile -> file
            is RealDirectory -> throw Exception("writeLines called on directory $file")
            is NonExistingFile -> file.mkfile()
        }
        this.file = fileReal
        val builder = StringBuilder()
        for (line in linesNotBlank) {
            builder.append(line)
            builder.append('\n')
        }
        fileReal.write(builder.toString().encodeToByteArray(), append)
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

    fun length(): Int {
        return when (val file = file) {
            is RealFile -> file.length()
            is RealDirectory -> throw Exception("length called on directory $file")
            is NonExistingFile -> 0
        }
    }

    fun delete() {
        file = when (val file = file) {
            is RealFile -> file.delete()
            is RealDirectory -> {
                for (child in file.children().toList()) {
                    DecsyncFile(child).delete()
                }
                file.delete()
            }
            is NonExistingFile -> file
        }
    }

    fun copy(dst: DecsyncFile) {
        when (val file = file) {
            is RealFile -> {
                val lines = readLines()
                dst.writeLines(lines)
            }
            is RealDirectory -> {
                for (child in file.children()) {
                    val isHidden = child.name[0] == '.'
                    var encodedName = child.name
                    if (isHidden) {
                        encodedName = encodedName.drop(1)
                    }
                    val name = Url.decode(encodedName) ?: continue

                    if (isHidden) {
                        hiddenChild(name).copy(dst.hiddenChild(name))
                    } else {
                        child(name).copy(dst.child(name))
                    }
                }
            }
            is NonExistingFile -> {}
        }
    }

    fun resetCache() = file.resetCache()

    override fun toString(): String = file.toString()

    fun listFilesRecursiveRelative(readBytesSrc: DecsyncFile? = null,
                                   pathPred: (List<String>) -> Boolean = { true }): List<ArrayList<String>> {
        return when (val file = file) {
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

                file.children().flatMap(fun(nativeFile: NativeFile): List<ArrayList<String>> {
                    val encodedName = nativeFile.name
                    if (encodedName[0] == '.') {
                        return emptyList()
                    }
                    val name = Url.decode(encodedName) ?: return emptyList()
                    if (!pathPred(listOf(name))) {
                        return emptyList()
                    }

                    val newReadBytesSrc = readBytesSrc?.child(name)
                    val newPred = { path: List<String> -> pathPred(listOf(name) + path) }
                    val paths = child(name).listFilesRecursiveRelative(newReadBytesSrc, newPred)
                    paths.forEach { it.add(0, name) }
                    return paths
                })
            }
            is NonExistingFile -> emptyList()
        }
    }

    fun listDirectories(): List<String> {
        return when (val file = file) {
            is RealFile -> throw Exception("listDirectory called on file $file")
            is RealDirectory -> file.children()
                    .filter { it.name[0] != '.' && it is RealDirectory }
                    .mapNotNull { Url.decode(it.name) }
            is NonExistingFile -> emptyList()
        }
    }
}
