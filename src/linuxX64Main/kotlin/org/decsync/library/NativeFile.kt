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

actual class NativeFile actual constructor(actual val path: String) {
    private val createMode = (S_IRWXU or S_IRWXG).toUInt()
    actual val fileType: FileType

    init {
        fileType = memScoped {
            val fileStat = alloc<stat>()
            if (stat(path, fileStat.ptr) != 0) {
                FileType.DOES_NOT_EXIST
            } else {
                when (fileStat.st_mode.toInt() and S_IFMT) {
                    S_IFREG -> FileType.FILE
                    S_IFDIR -> FileType.DIRECTORY
                    else -> throw IOException("Unknown file type for file $path")
                }
            }
        }
    }

    actual fun children(): List<String> {
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
    actual fun child(name: String): NativeFile = NativeFile("$path/$name")

    actual fun createParent() {
        val parentPath = path.dropLastWhile { it != '/' }.dropLast(1)
        if (parentPath.isEmpty()) return
        val parentFile = NativeFile(parentPath)
        if (parentFile.fileType != FileType.DOES_NOT_EXIST) return
        parentFile.createParent()
        mkdir(parentPath, createMode)
    }
    actual fun delete() {
        when (fileType) {
            FileType.FILE -> deleteFile()
            FileType.DIRECTORY -> {
                for (name in children()) {
                    child(name).delete()
                }
                deleteDirectory()
            }
            FileType.DOES_NOT_EXIST -> {}
        }
    }
    private fun deleteFile() {
        unlink(path)
    }
    private fun deleteDirectory() {
        rmdir(path)
    }
    actual fun length(): Int {
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
    actual fun read(readBytes: Int): ByteArray {
        val fd = open(path, O_RDONLY)
        val len = length(fd)
        val buf = ByteArray(len - readBytes + 1)
        lseek(fd, readBytes.toLong(), SEEK_SET)
        buf.usePinned { bufPin ->
            read(fd, bufPin.addressOf(0), len.toULong())
        }
        close(fd)
        return buf
    }
    actual fun write(text: ByteArray, append: Boolean) {
        if (text.isEmpty()) {
            if (!append) {
                deleteFile()
            }
            return
        }
        val fd = open(path, O_CREAT or O_WRONLY or if (append) O_APPEND else 0, createMode)
        text.usePinned { textPin ->
            write(fd, textPin.addressOf(0), text.size.toULong())
        }
    }
}