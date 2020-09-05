/**
 * libdecsync - NativeFile.kt
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

sealed class FileSystemNode(val name: String)

sealed class RealNode(name: String) : FileSystemNode(name)

abstract class RealFile(name: String) : RealNode(name) {
    abstract fun delete()
    abstract fun length(): Int
    abstract fun read(readBytes: Int = 0): ByteArray
    abstract fun write(text: ByteArray, append: Boolean = false)
}

abstract class RealDirectory(name: String) : RealNode(name) {
    protected abstract fun listChildren(): List<RealNode>
    abstract fun delete()
    abstract fun mkfile(name: String, text: ByteArray): RealFile
    abstract fun mkdir(name: String): RealDirectory

    private var mChildren: MutableList<NativeFile>? = null
    fun children(nativeFile: NativeFile): List<NativeFile> = mChildren ?: run {
        listChildren().map { NativeFile(it, nativeFile) }
    }.also {
        mChildren = it.toMutableList()
    }
    fun resetCache() {
        mChildren = null
    }

    fun addChild(nativeFile: NativeFile) {
        mChildren?.add(nativeFile)
    }
}

class NonExistingNode(
        name: String,
        val parent: NativeFile
) : FileSystemNode(name) {
    private val children: MutableList<NativeFile> = mutableListOf()
    fun children(): List<NativeFile> = children
    fun addChild(nativeFile: NativeFile) {
        children.add(nativeFile)
    }
}

class NativeFile constructor(
        fileSystemNode: FileSystemNode,
        private val parent: NativeFile?
) {
    // The name doesn't change, even when fileSystemNode does
    val name = fileSystemNode.name
    var fileSystemNode = fileSystemNode
        private set

    fun child(name: String): NativeFile {
        return when (val node = fileSystemNode) {
            is RealFile -> throw Exception("child called on file $node")
            is RealDirectory -> {
                node.children(this).find { it.name == name } ?: run {
                    NativeFile(NonExistingNode(name, this), this).also {
                        node.addChild(it)
                    }
                }
            }
            is NonExistingNode -> {
                node.children().find { it.name == name } ?: run {
                    NativeFile(NonExistingNode(name, this), this).also {
                        node.addChild(it)
                    }
                }
            }
        }
    }

    fun read(readBytes: Int = 0): ByteArray? {
        return when (val node = fileSystemNode) {
            is RealFile -> node.read(readBytes).also { bytes ->
                // There should never be an empty file
                // It probably means that an (uncaught) error occurred
                if (readBytes == 0 && bytes.isEmpty()) {
                    throw Exception("Read empty file: $node")
                }
            }
            is RealDirectory -> throw Exception("read called on directory $node")
            is NonExistingNode -> null
        }
    }

    fun write(text: ByteArray, append: Boolean = false) {
        when (val node = fileSystemNode) {
            is RealFile -> {
                // Make sure we do not create empty files
                if (text.isEmpty()) {
                    if (!append) {
                        if (parent == null) throw Exception("Cannot remove the root file")
                        node.delete()
                        fileSystemNode = NonExistingNode(name, parent)
                    }
                    return
                }
                node.write(text, append)
            }
            is RealDirectory -> throw Exception("write called on directory $node")
            is NonExistingNode -> {
                // Make sure we do not create empty files
                if (text.isEmpty()) {
                    return
                }
                fileSystemNode = node.parent.mkdir().mkfile(name, text)
            }
        }
    }

    fun length(): Int {
        return when (val node = fileSystemNode) {
            is RealFile -> node.length()
            is RealDirectory -> throw Exception("length called on directory $node")
            is NonExistingNode -> 0
        }
    }

    fun children(): List<NativeFile> {
        return when (val node = fileSystemNode) {
            is RealFile -> throw Exception("children called on file $node")
            is RealDirectory -> node.children(this)
            is NonExistingNode -> emptyList()
        }
    }

    fun deleteRecursive() {
        if (parent == null) throw Exception("Cannot remove the root file")
        when (val node = fileSystemNode) {
            is RealFile -> {
                node.delete()
                fileSystemNode = NonExistingNode(name, parent)
            }
            is RealDirectory -> {
                node.children(this).forEach { child ->
                    child.deleteRecursive()
                }
                node.delete()
                fileSystemNode = NonExistingNode(name, parent)
            }
            is NonExistingNode -> {}
        }
    }

    fun mkdir(): RealDirectory {
        return when (val node = fileSystemNode) {
            is RealFile -> throw Exception("Cannot create directory from file $node")
            is RealDirectory -> node
            is NonExistingNode -> node.parent.mkdir().mkdir(name).also { fileSystemNode = it }
        }
    }

    // Invalidates all child instances
    fun resetCache() {
        when (val node = fileSystemNode) {
            is RealFile -> {}
            is RealDirectory -> node.resetCache()
            is NonExistingNode -> {}
        }
    }

    override fun toString(): String = "${parent ?: ""}/$name"
}