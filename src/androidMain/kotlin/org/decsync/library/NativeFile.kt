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

import java.io.File

actual class NativeFile actual constructor(actual val path: String) {
    private val file: File = File(path)

    actual val fileType: FileType =
            when {
                file.isFile -> FileType.FILE
                file.isDirectory -> FileType.DIRECTORY
                else -> FileType.DOES_NOT_EXIST
            }

    actual fun children(): List<String> = (file.list() ?: emptyArray()).asList()
    actual fun child(name: String): NativeFile = NativeFile("$path/$name")

    actual fun createParent() {
        file.parentFile.mkdirs()
    }
    actual fun delete() {
        file.deleteRecursively()
    }
    actual fun length(): Int = file.length().toInt()
    actual fun read(readBytes: Int): ByteArray =
            file.inputStream().use { input ->
                input.skip(readBytes.toLong())
                input.readBytes()
            }
    actual fun write(text: ByteArray, append: Boolean) =
            if (append) {
                file.appendBytes(text)
            } else {
                file.writeBytes(text)
            }
}