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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.io.IOException

@ExperimentalStdlibApi
class RealFileImpl(
        val parent: DecsyncFile,
        val context: Context,
        val uri: Uri,
        override val name: String,
        var length: Int
) : RealFile() {
    override fun delete(): NonExistingFile {
        val cr = context.contentResolver
        DocumentsContract.deleteDocument(cr, uri)
        (parent.file as? RealDirectoryImpl)?.removeChild(this)
        return NonExistingFileImpl(parent, name)
    }
    override fun length(): Int = length
    override fun read(readBytes: Int): ByteArray {
        val cr = context.contentResolver
        return cr.openInputStream(uri)?.use { input ->
            input.skip(readBytes.toLong())
            input.readBytes()
        } ?: throw IOException("Could not open input stream for file $this")
    }
    override fun write(text: ByteArray, append: Boolean) {
        val mode = if (append) "wa" else "w"
        val cr = context.contentResolver
        cr.openOutputStream(uri, mode)?.use { output ->
            output.write(text)
        } ?: throw IOException("Could not open output stream for file $this")
        if (append)
            length += text.size
        else
            length = text.size
    }

    override fun toString(): String = uri.toString()
}

@ExperimentalStdlibApi
class RealDirectoryImpl(
        val parent: DecsyncFile?,
        val context: Context,
        val uri: Uri,
        override val name: String
) : RealDirectory() {
    val asDecsyncFile = DecsyncFile(this)
    private var _children: MutableList<NativeFile>? = null

    override fun resetCache() {
        _children = null
    }
    override fun child(name: String): NativeFile =
            children().firstOrNull { it.name == name }
                    ?: NonExistingFileImpl(asDecsyncFile, name)

    override fun children(): List<NativeFile> = _children ?: {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
        val cr = context.contentResolver
        val result = mutableListOf<NativeFile>()
        cr.query(childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0)
                val mimeType = cursor.getString(1)
                val displayName = cursor.getString(2)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                result += if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    RealDirectoryImpl(asDecsyncFile, context, childUri, displayName)
                } else {
                    val length = cursor.getLong(3).toInt()
                    RealFileImpl(asDecsyncFile, context, childUri, displayName, length)
                }
            }
        } ?: throw IOException("Could not get children for directory $this")
        result
    }().also { _children = it }

    fun addChild(file: NativeFile) {
        _children?.let { it.add(file) }
    }
    fun removeChild(file: NativeFile) {
        _children?.let {  it.remove(file) }
    }

    override fun delete(): NonExistingFile {
        if (parent == null) {
            throw IOException("Cannot delete root directory")
        }

        val cr = context.contentResolver
        DocumentsContract.deleteDocument(cr, uri)
        (parent.file as? RealDirectoryImpl)?.removeChild(this)
        return NonExistingFileImpl(parent, name)
    }

    override fun toString(): String = uri.toString()
}

@ExperimentalStdlibApi
class NonExistingFileImpl(
        val parent: DecsyncFile,
        override val name: String
) : NonExistingFile() {
    private val asDecsyncFile = DecsyncFile(this)

    override fun child(name: String): NativeFile = NonExistingFileImpl(asDecsyncFile, name)

    override fun mkfile(): RealFile {
        val parentDir = createParentDir()
        return when (val result = parentDir.child(name)) {
            is RealFile -> result
            is RealDirectory -> throw IOException("Directory $result used as file")
            is NonExistingFile -> {
                val cr = parentDir.context.contentResolver
                val uri = DocumentsContract.createDocument(cr, parentDir.uri, "", name)
                        ?: throw IOException("Could not create file $name of parent $parentDir")
                RealFileImpl(parentDir.asDecsyncFile, parentDir.context, uri, name, 0)
                        .also { parentDir.addChild(it) }
            }
        }
    }

    private fun mkdir(): RealDirectoryImpl {
        val parentDir = createParentDir()
        return when (val result = parentDir.child(name)) {
            is RealFile -> throw IOException("File $result used as directory")
            is RealDirectoryImpl -> result
            is NonExistingFile -> {
                val cr = parentDir.context.contentResolver
                Log.d("parentDir.uri: ${parentDir.uri}")
                Log.d("name: $name")
                val uri = DocumentsContract.createDocument(cr, parentDir.uri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                        ?: throw IOException("Could not create file $name of parent $parentDir")
                RealDirectoryImpl(parentDir.asDecsyncFile, parentDir.context, uri, name)
                        .also { parentDir.addChild(it) }
            }
            else -> throw IOException("Non-standard file type found. This should never happen.")
        }
    }

    private fun createParentDir(): RealDirectoryImpl {
        return when (val parentNativeFile = parent.file) {
            is RealFileImpl -> throw IOException("File $parent used as directory")
            is RealDirectoryImpl -> parentNativeFile
            is NonExistingFileImpl -> parentNativeFile.mkdir().also { parent.file = it }
            else -> throw IOException("Non-standard file type found. This should never happen.")
        }
    }

    override fun toString(): String = "$parent/$name"
}