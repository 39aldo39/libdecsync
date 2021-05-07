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

import kotlinx.cinterop.*
import platform.posix.*

const val createModeDir = S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH
const val createModeFile = S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH

class RealFileImpl(private val path: String, name: String) : RealFile(name) {
    override fun delete() {
        unlink(path)
    }
    override fun length(): Int {
        val fd = open(path, openFlagsBinary or O_RDONLY)
        val result = length(fd)
        close(fd)
        return result
    }
    private fun length(fd: Int): Int = memScoped {
        val fileStat = alloc<stat>()
        fstat(fd, fileStat.ptr)
        return fileStat.st_size.toInt()
    }
    override fun read(readBytes: Int): ByteArray {
        val fd = open(path, openFlagsBinary or O_RDONLY)
        if (fd < 0) throw Exception("Failed to open $path")
        val len = length(fd)
        if (len <= readBytes) {
            return ByteArray(0)
        }
        val buf = ByteArray(len - readBytes)
        lseek(fd, readBytes.off_t(), SEEK_SET)
        buf.usePinned { bufPin ->
            readCustom(fd, bufPin.addressOf(0), (len - readBytes))
        }
        close(fd)
        return buf
    }
    override fun write(text: ByteArray, append: Boolean) {
        val flags = openFlagsBinary or O_CREAT or O_WRONLY or if (append) O_APPEND else O_TRUNC
        val fd = open(path, flags, createModeFile)
        if (fd < 0) throw Exception("Failed to open $path")
        text.usePinned { textPin ->
            writeCustom(fd, textPin.addressOf(0), text.size)
        }
        close(fd)
    }

    override fun toString(): String = path
}

class RealDirectoryImpl(private val path: String, name: String) : RealDirectory(name) {
    override fun listChildren(): List<RealNode> {
        val result = mutableListOf<RealNode>()
        val d = opendir(path) ?: return emptyList()
        while (true) {
            val dir = readdir(d)?.pointed ?: break
            val name = dir.d_name.toKString()
            if (name == "." || name == "..") continue
            result += realNodeFromPath("$path/$name", name) ?: continue
        }
        closedir(d)
        return result
    }
    override fun delete() {
        rmdir(path)
    }
    override fun mkfile(name: String, text: ByteArray): RealFile {
        val file = RealFileImpl("$path/$name", name)
        file.write(text)
        return file
    }
    override fun mkdir(name: String): RealDirectory {
        mkdirCustom("$path/$name", createModeDir)
        return RealDirectoryImpl("$path/$name", name)
    }

    override fun toString(): String = path
}

fun nativeFileFromPath(path: String, name: String = path.takeLastWhile { it != '/' }): NativeFile {
    val node = realNodeFromPath(path, name) ?: run {
        mkdirs(path)
        RealDirectoryImpl(path, name)
    }
    return NativeFile(node, null)
}

private fun mkdirs(path: String) {
    if (path.isEmpty() || realNodeFromPath(path) != null) return
    val parentPath = path.dropLastWhile { it != '/' }.dropLast(1)
    mkdirs(parentPath)
    mkdirCustom(path, createModeDir)
}

private fun realNodeFromPath(path: String, name: String = path.takeLastWhile { it != '/' }): RealNode? = memScoped {
    val fileStat = alloc<stat>()
    if (stat(path, fileStat.ptr) != 0) {
        null
    } else {
        when (fileStat.st_mode.toInt() and S_IFMT) {
            S_IFREG -> RealFileImpl(path, name)
            S_IFDIR -> RealDirectoryImpl(path, name)
            else -> throw Exception("Unknown file type for file $path")
        }
    }
}