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

sealed class NativeFile {
    abstract val name: String
    abstract fun child(name: String): NativeFile
    open fun resetCache() = Unit
}

abstract class RealFile : NativeFile() {
    override fun child(name: String): NativeFile = throw Exception("child called on file $this")
    abstract fun delete(): NonExistingFile
    abstract fun length(): Int
    abstract fun read(readBytes: Int = 0): ByteArray
    abstract fun write(text: ByteArray, append: Boolean = false)
}

abstract class RealDirectory : NativeFile() {
    abstract fun children(): List<NativeFile>
    abstract fun delete(): NonExistingFile
}

abstract class NonExistingFile : NativeFile() {
    abstract fun mkfile(): RealFile
}