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

import kotlinx.io.IOException

@ExperimentalStdlibApi
class DecsyncFile(
        private var file: NativeFile
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

    fun child(names: Iterable<String>): DecsyncFile {
        var result = this
        for (name in names) {
            result = result.child(name)
        }
        return result
    }

    fun child(vararg names: String): DecsyncFile = child(names.asIterable())

    fun readLines(cr: ContentResolver, readBytes: Int = 0): List<String> {
        return when (val file = file) {
            is RealFile ->
                byteArrayToString(file.read(cr, readBytes))
                        .split('\n')
                        .filter { it.isNotBlank() }
            is RealDirectory -> throw IOException("readLines called on directory $file")
            is NonExistingFile -> emptyList()
        }
    }

    fun writeLines(cr: ContentResolver, lines: Iterable<String>, append: Boolean = false) {
        val fileReal = when (val file = file) {
            is RealFile -> file
            is RealDirectory -> throw IOException("writeLines called on directory $file")
            is NonExistingFile -> file.mkfile()
        }
        this.file = fileReal
        val builder = StringBuilder()
        for (line in lines) {
            builder.append(line)
            builder.append('\n')
        }
        fileReal.write(cr, builder.toString().encodeToByteArray(), append)
    }

    fun readText(cr: ContentResolver): String? {
        val lines = readLines(cr)
        if (lines.size != 1) {
            return null
        }
        return lines[0]
    }

    fun writeText(cr: ContentResolver, text: String) = writeLines(cr, listOf(text))

    fun length(): Int {
        return when (val file = file) {
            is RealFile -> file.length()
            is RealDirectory -> throw IOException("length called on directory $file")
            is NonExistingFile -> 0
        }
    }

    fun delete() {
        return when (val file = file) {
            is RealFile -> file.delete()
            is RealDirectory -> {
                for (child in file.childrenFiles()) {
                    DecsyncFile(child).delete()
                }
                file.delete()
            }
            is NonExistingFile -> {}
        }
    }

    fun copy(cr: ContentResolver, dst: DecsyncFile) {
        when (val file = file) {
            is RealFile -> {
                val lines = readLines(cr)
                dst.writeLines(cr, lines)
            }
            is RealDirectory -> {
                for (childName in file.children()) {
                    val isHidden = childName[0] == '.'
                    var encodedName = childName
                    if (isHidden) {
                        encodedName = encodedName.drop(1)
                    }
                    val name = Url.decode(encodedName) ?: continue

                    if (isHidden) {
                        hiddenChild(name).copy(cr, dst.hiddenChild(name))
                    } else {
                        child(name).copy(cr, dst.child(name))
                    }
                }
            }
            is NonExistingFile -> {}
        }
    }

    override fun toString(): String = file.toString()

    fun listFilesRecursiveRelative(cr: ContentResolver, readBytesSrc: DecsyncFile? = null,
                                   pathPred: (List<String>) -> Boolean = { true }): List<ArrayList<String>> {
        return when (val file = file) {
            is RealFile -> listOf(arrayListOf())
            is RealDirectory -> {
                // Skip equal sequence numbers
                if (readBytesSrc != null) {
                    val seqFile = hiddenChild("decsync-sequence")
                    val seq = seqFile.readText(cr)
                    val readBytesSeqFile = readBytesSrc.hiddenChild("decsync-sequence")
                    val readBytesSeq = readBytesSeqFile.readText(cr)
                    if (seq != null) {
                        if (seq == readBytesSeq) {
                            return emptyList()
                        } else {
                            readBytesSeqFile.writeText(cr, seq)
                        }
                    }
                }

                file.children().flatMap(fun(encodedName: String): List<ArrayList<String>> {
                    if (encodedName[0] == '.') {
                        return emptyList()
                    }
                    val name = Url.decode(encodedName) ?: return emptyList()
                    if (!pathPred(listOf(name))) {
                        return emptyList()
                    }

                    val newReadBytesSrc = readBytesSrc?.child(name)
                    val newPred = { path: List<String> -> pathPred(listOf(name) + path) }
                    val paths = child(name).listFilesRecursiveRelative(cr, newReadBytesSrc, newPred)
                    paths.forEach { it.add(0, name) }
                    return paths
                })
            }
            is NonExistingFile -> emptyList()
        }
    }

    fun listDirectories(): List<String> {
        return when (val file = file) {
            is RealFile -> throw IOException("listDirectory called on file $file")
            is RealDirectory -> file.children()
                    .filter { it[0] != '.' && file.child(it) is RealDirectory }
                    .mapNotNull { Url.decode(it) }
            is NonExistingFile -> emptyList()
        }
    }
}
