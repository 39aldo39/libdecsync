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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream

// Implementations using the Storage Access Framework (SAF)

@ExperimentalStdlibApi
class RealFileSaf(
        val context: Context,
        val uri: Uri,
        name: String,
        var length: Int
) : RealFile(name) {

    override fun delete() {
        val cr = context.contentResolver
        DocumentsContract.deleteDocument(cr, uri)
    }

    override fun length(): Int = length

    override fun read(readBytes: Int): ByteArray {
        val cr = context.contentResolver
        return cr.openInputStream(uri)?.use { input ->
            input.skip(readBytes.toLong())
            input.readBytes()
        } ?: throw Exception("Could not open input stream for file $this")
    }

    override fun write(text: ByteArray, append: Boolean) {
        val mode = if (append) "wa" else "w"
        val cr = context.contentResolver
        cr.openOutputStream(uri, mode)?.use { output ->
            if (!append) {
                (output as FileOutputStream).channel.truncate(0)
            }
            output.write(text)
        } ?: throw Exception("Could not open output stream for file $this")
        if (append)
            length += text.size
        else
            length = text.size
    }

    override fun toString(): String = uri.toString()
}

@ExperimentalStdlibApi
class RealDirectorySaf(
        val context: Context,
        val uri: Uri,
        name: String
) : RealDirectory(name) {

    override fun listChildren(): List<RealNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
        val cr = context.contentResolver
        val result = mutableListOf<RealNode>()
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
                    RealDirectorySaf(context, childUri, displayName)
                } else {
                    val length = cursor.getLong(3).toInt()
                    RealFileSaf(context, childUri, displayName, length)
                }
            }
        } ?: throw Exception("Could not get children for directory $this")
        return result
    }

    override fun delete() {
        val cr = context.contentResolver
        DocumentsContract.deleteDocument(cr, uri)
    }

    override fun mkfile(name: String, text: ByteArray): RealFile {
        val cr = context.contentResolver
        val uri = DocumentsContract.createDocument(cr, uri, "", name)
                ?: throw Exception("Could not create file $name of parent $this")
        return RealFileSaf(context, uri, name, 0).apply { write(text) }
    }

    override fun mkdir(name: String): RealDirectory {
        val cr = context.contentResolver
        val uri = DocumentsContract.createDocument(cr, uri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                ?: throw Exception("Could not create directory $name of parent $this")
        return RealDirectorySaf(context, uri, name)
    }

    override fun toString(): String = uri.toString()
}

/**
 * Returns a [NativeFile] given a document tree [uri] from SAF with a [context].
 *
 * @throws InsufficientAccessException if there is insufficient access to the [uri].
 */
@ExperimentalStdlibApi
fun nativeFileFromDirUri(context: Context, uri: Uri): NativeFile {
    checkUriPermissions(context, uri)
    val name = DecsyncPrefUtils.getNameFromUri(context, uri)
    return NativeFile(RealDirectorySaf(context, uri, name), null)
}

fun checkUriPermissions(context: Context, uri: Uri) {
    if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED ||
            context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
        throw InsufficientAccessException()
    }
}

// Implementations using a traditional file

class RealFileSys(
        val file: File,
        name: String = file.name
) : RealFile(name) {

    override fun delete() {
        file.delete()
    }

    override fun length(): Int = file.length().toInt()

    override fun read(readBytes: Int): ByteArray =
            file.inputStream().use { input ->
                input.skip(readBytes.toLong())
                input.readBytes()
            }

    override fun write(text: ByteArray, append: Boolean) =
            if (append) {
                file.appendBytes(text)
            } else {
                file.writeBytes(text)
            }
}

class RealDirectorySys(
        val file: File,
        name: String = file.name
) : RealDirectory(name) {

    override fun listChildren(): List<RealNode> {
        return (file.listFiles() ?: emptyArray()).asList().mapNotNull { realNodeFromFile(it) }
    }

    override fun delete() {
        file.delete()
    }

    override fun mkfile(name: String, text: ByteArray): RealFile {
        return RealFileSys(File(file, name), name).apply { write(text) }
    }

    override fun mkdir(name: String): RealDirectory {
        val dir = File(file, name)
        dir.mkdir()
        return RealDirectorySys(dir, name)
    }
}

/**
 * Returns a [NativeFile] given a [file].
 */
fun nativeFileFromFile(file: File): NativeFile {
    val node = realNodeFromFile(file) ?: run {
        file.mkdirs()
        RealDirectorySys(file)
    }
    return NativeFile(node, null)
}

private fun realNodeFromFile(file: File, name: String = file.name): RealNode? =
        when {
            file.isFile -> RealFileSys(file, name)
            file.isDirectory -> RealDirectorySys(file, name)
            else -> null
        }