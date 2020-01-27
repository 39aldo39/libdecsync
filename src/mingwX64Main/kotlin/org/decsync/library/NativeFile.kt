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
import kotlinx.io.IOException
import platform.posix.*

actual abstract class ContentResolver
object CR : ContentResolver()

val createMode = (S_IRWXU or S_IRWXG).toUInt()

class RealFileImpl(private val path: String) : RealFile() {
    override fun child(name: String): NativeFile = throw IOException("child called on file $this")

    override fun delete() {
        unlink(path)
    }
    override fun length(): Int {
        val fd = open(path, O_RDONLY)
        val result = length(fd)
        close(fd)
        return result
    }
    private fun length(fd: Int): Int = memScoped {
        val fileStat = alloc<stat>()
        fstat(fd, fileStat.ptr)
        return fileStat.st_size.toInt()
    }
    override fun read(cr: ContentResolver, readBytes: Int): ByteArray {
        val fd = open(path, O_RDONLY)
        val len = length(fd)
        val buf = ByteArray(len - readBytes + 1)
        lseek(fd, readBytes, SEEK_SET)
        buf.usePinned { bufPin ->
            read(fd, bufPin.addressOf(0), len.toUInt())
        }
        close(fd)
        return buf
    }
    override fun write(cr: ContentResolver, text: ByteArray, append: Boolean) {
        if (text.isEmpty()) {
            if (!append) {
                delete()
            }
            return
        }
        val fd = open(path, O_CREAT or O_WRONLY or if (append) O_APPEND else 0, createMode)
        text.usePinned { textPin ->
            write(fd, textPin.addressOf(0), text.size.toUInt())
        }
    }

    override fun toString(): String = path
}

class RealDirectoryImpl(private val path: String) : RealDirectory() {
    override fun child(name: String): NativeFile = getNativeFileFromPath("$path/$name")

    override fun children(): List<String> {
        val result = mutableListOf<String>()
        val d = opendir(path) ?: return emptyList()
        while (true) {
            val dir = readdir(d)?.pointed ?: break
            val name = dir.d_name.toKString()
            if (name == "." || name == "..") continue
            result += name
        }
        closedir(d)
        return result
    }
    override fun childrenFiles(): List<NativeFile> = children().map { child(it) }
    override fun delete() {
        rmdir(path)
    }

    override fun toString(): String = path
}

class NonExistingFileImpl(private val path: String) : NonExistingFile() {
    override fun child(name: String): NativeFile = NonExistingFileImpl("$path/$name")

    // We only create the parent directory, the file is created automatically
    override fun mkfile(): RealFile {
        val result = RealFileImpl(path)
        val parentPath = path.dropLastWhile { it != '/' }.dropLast(1)
        if (parentPath.isEmpty()) return result
        val parentFile = getNativeFileFromPath(parentPath)
        if (parentFile !is NonExistingFile) return result
        parentFile.mkfile()
        mkdir(parentPath)
        return result
    }

    override fun toString(): String = path
}

fun getNativeFileFromPath(path: String): NativeFile = memScoped {
    val fileStat = alloc<stat>()
    if (stat(path, fileStat.ptr) != 0) {
        return NonExistingFileImpl(path)
    } else {
        when (fileStat.st_mode.toInt() and S_IFMT) {
            S_IFREG -> RealFileImpl(path)
            S_IFDIR -> RealDirectoryImpl(path)
            else -> throw IOException("Unknown file type for file $path")
        }
    }
}