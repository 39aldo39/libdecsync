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

import androidx.documentfile.provider.DocumentFile
import kotlinx.io.IOException

actual typealias ContentResolver = android.content.ContentResolver

class VarNativeFile(var nativeFile: NativeFile)

class RealFileImpl(val file: DocumentFile) : RealFile() {
    override fun child(name: String): NativeFile = throw IOException("child called on file $this")

    override fun delete() {
        file.delete()
    }
    override fun length(): Int = file.length().toInt()
    override fun read(cr: ContentResolver, readBytes: Int): ByteArray =
            cr.openInputStream(file.uri)?.use { input ->
                input.skip(readBytes.toLong())
                input.readBytes()
            } ?: throw IOException("Could not open input stream for file $this")
    override fun write(cr: ContentResolver, text: ByteArray, append: Boolean) {
        val mode = if (append) "wa" else "w"
        cr.openOutputStream(file.uri, mode)?.use { output ->
            output.write(text)
        } ?: throw IOException("Could not open output stream for file $this")
    }

    override fun toString(): String = file.uri.toString()
}

class RealDirectoryImpl(val file: DocumentFile) : RealDirectory() {
    private val thisAsVarNativeFile = VarNativeFile(this)

    override fun child(name: String): NativeFile =
            file.findFile(name)?.let {
                if (it.isFile) RealFileImpl(it) else RealDirectoryImpl(it)
            } ?: NonExistingFileImpl(thisAsVarNativeFile, name)

    override fun children(): List<String> = file.listFiles().asList().mapNotNull { it.name }
    override fun childrenFiles(): List<NativeFile> = file.listFiles().asList().map { file ->
        if (file.isFile) RealFileImpl(file) else RealDirectoryImpl(file)
    }

    override fun delete() {
        file.delete()
    }

    override fun toString(): String = file.uri.toString()
}

class NonExistingFileImpl(val parent: VarNativeFile, val name: String) : NonExistingFile() {
    private val thisAsVarNativeFile = VarNativeFile(this)

    override fun child(name: String): NativeFile = NonExistingFileImpl(thisAsVarNativeFile, name)

    override fun mkfile(): RealFile {
        val parentDocumentFile = getParentDocumentFile()
        val result = parentDocumentFile.findFile(name)
        if (result != null) return RealFileImpl(result)
        val documentFile = parentDocumentFile.createFile("", name) ?: throw IOException("Could not create file $name of parent $parentDocumentFile")
        return RealFileImpl(documentFile)
    }

    private fun mkdir(): DocumentFile {
        val parentDocumentFile = getParentDocumentFile()
        val result = parentDocumentFile.findFile(name)
        if (result != null) return result
        return parentDocumentFile.createDirectory(name) ?: throw IOException("Could not create directory $name of parent $parentDocumentFile")
    }

    private fun getParentDocumentFile(): DocumentFile {
        return when (val parentNativeFile = parent.nativeFile) {
            is RealFileImpl -> throw IOException("Cannot create child of file $parent")
            is RealDirectoryImpl -> parentNativeFile.file
            is NonExistingFileImpl -> parentNativeFile.mkdir().also { parent.nativeFile = RealDirectoryImpl(it) }
            else -> throw IOException("Non-standard file type found. This should never happen.")
        }
    }

    override fun toString(): String = "$parent/$name"
}