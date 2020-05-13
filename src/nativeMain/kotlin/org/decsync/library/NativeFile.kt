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

expect val openFlagsBinary: Int
const val createModeDir = S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH
const val createModeFile = S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH
expect fun mkdirCustom(path: String, mode: Int)
expect fun readCustom(fd: Int, buf: CValuesRef<*>?, len: Int)
expect fun writeCustom(fd: Int, buf: CValuesRef<*>?, size: Int)

class RealFileImpl(private val path: String, override val name: String) : RealFile() {
    override fun delete(): NonExistingFile {
        unlink(path)
        return NonExistingFileImpl(path, name)
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
        val buf = ByteArray(len - readBytes + 1)
        lseek(fd, readBytes.off_t(), SEEK_SET)
        buf.usePinned { bufPin ->
            readCustom(fd, bufPin.addressOf(0), len)
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

class RealDirectoryImpl(private val path: String, override val name: String) : RealDirectory() {
    override fun child(name: String): NativeFile = getNativeFileFromPath("$path/$name", name)

    override fun children(): List<NativeFile> {
        val result = mutableListOf<NativeFile>()
        val d = opendir(path) ?: return emptyList()
        while (true) {
            val dir = readdir(d)?.pointed ?: break
            val name = dir.d_name.toKString()
            if (name == "." || name == "..") continue
            result += getNativeFileFromPath("$path/$name", name)
        }
        closedir(d)
        return result
    }
    override fun delete(): NonExistingFile {
        rmdir(path)
        return NonExistingFileImpl(path, name)
    }

    override fun toString(): String = path
}

class NonExistingFileImpl(private val path: String, override val name: String) : NonExistingFile() {
    override fun child(name: String): NativeFile = NonExistingFileImpl("$path/$name", name)

    // We only create the parent directory, the file is created automatically
    override fun mkfile(): RealFile {
        val result = RealFileImpl(path, name)
        val parentPath = path.dropLastWhile { it != '/' }.dropLast(1)
        if (parentPath.isEmpty()) return result
        val parentFile = getNativeFileFromPath(parentPath)
        if (parentFile !is NonExistingFile) return result
        parentFile.mkfile()
        mkdirCustom(parentPath, createModeDir)
        return result
    }

    override fun toString(): String = path
}

fun getNativeFileFromPath(path: String, name: String = path.takeLastWhile { it != '/' }): NativeFile = memScoped {
    val fileStat = alloc<stat>()
    if (stat(path, fileStat.ptr) != 0) {
        return NonExistingFileImpl(path, name)
    } else {
        when (fileStat.st_mode.toInt() and S_IFMT) {
            S_IFREG -> RealFileImpl(path, name)
            S_IFDIR -> RealDirectoryImpl(path, name)
            else -> throw Exception("Unknown file type for file $path")
        }
    }
}