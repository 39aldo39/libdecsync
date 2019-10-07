/**
 * libdecsync - FileUtils.kt
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

enum class FileType {
    FILE, DIRECTORY, DOES_NOT_EXIST
}

expect class NativeFile(path: String) {
    val path: String
    val fileType: FileType

    fun children(): List<String>
    fun child(name: String): NativeFile

    fun createParent()
    fun delete()
    fun length(): Int
    fun read(readBytes: Int = 0): ByteArray
    fun write(text: ByteArray, append: Boolean = false)
}