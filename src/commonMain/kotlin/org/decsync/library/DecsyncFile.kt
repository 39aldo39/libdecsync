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
        private val file: NativeFile
) {
    constructor(dir: String): this(NativeFile(dir))

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

    fun readLines(readBytes: Int = 0): List<String> =
            when (file.fileType) {
                FileType.FILE ->
                    byteArrayToString(file.read(readBytes))
                            .split('\n')
                            .filter { it.isNotBlank() }
                FileType.DIRECTORY -> throw IOException("readLines called on directory ${file.path}")
                FileType.DOES_NOT_EXIST -> emptyList()
            }

    fun writeLines(lines: Iterable<String>, append: Boolean = false) {
        when (file.fileType) {
            FileType.FILE -> {}
            FileType.DIRECTORY -> throw IOException("writeLines called on directory ${file.path}")
            FileType.DOES_NOT_EXIST -> file.createParent()
        }
        val builder = StringBuilder()
        for (line in lines) {
            builder.append(line)
            builder.append('\n')
        }
        file.write(builder.toString().encodeToByteArray(), append)
    }

    fun readText(): String? {
        val lines = readLines()
        if (lines.size != 1) {
            return null
        }
        return lines[0]
    }

    fun writeText(text: String) = writeLines(listOf(text))

    fun length() =
            when (file.fileType) {
                FileType.FILE -> file.length()
                FileType.DIRECTORY -> throw IOException("length called on directory ${file.path}")
                FileType.DOES_NOT_EXIST -> 0
            }

    fun delete() = file.delete()

    fun copy(dst: DecsyncFile) {
        when (file.fileType) {
            FileType.FILE -> {
                val lines = readLines()
                dst.writeLines(lines)
            }
            FileType.DIRECTORY -> {
                for (childName in file.children()) {
                    val isHidden = childName[0] == '.'
                    var encodedName = childName
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
            FileType.DOES_NOT_EXIST -> {}
        }
    }

    override fun toString(): String = file.path

    fun listFilesRecursiveRelative(readBytesSrc: DecsyncFile? = null,
                                   pathPred: (List<String>) -> Boolean = { true }): List<ArrayList<String>> {
        return when (file.fileType) {
            FileType.FILE -> listOf(arrayListOf())
            FileType.DIRECTORY -> {
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
                    val paths = child(name).listFilesRecursiveRelative(newReadBytesSrc, newPred)
                    paths.forEach { it.add(0, name) }
                    return paths
                })
            }
            else -> emptyList()
        }
    }

    fun listDirectories(): List<String> =
            file.children()
                    .filter { it[0] != '.' && file.child(it).fileType == FileType.DIRECTORY }
                    .mapNotNull { Url.decode(it) }
}
