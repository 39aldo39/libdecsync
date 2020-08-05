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
        val parent: DecsyncFile,
        val context: Context,
        val uri: Uri,
        override val name: String,
        var length: Int
) : RealFile() {
    override fun delete(): NonExistingFile {
        val cr = context.contentResolver
        DocumentsContract.deleteDocument(cr, uri)
        (parent.file as? RealDirectorySaf)?.removeChild(this)
        return NonExistingFileSaf(parent, name)
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
                    ?: NonExistingFileSaf(asDecsyncFile, name)

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
                    RealDirectorySaf(asDecsyncFile, context, childUri, displayName)
                } else {
                    val length = cursor.getLong(3).toInt()
                    RealFileSaf(asDecsyncFile, context, childUri, displayName, length)
                }
            }
        } ?: throw Exception("Could not get children for directory $this")
        result
    }().also { _children = it }

    fun addChild(file: NativeFile) {
        _children?.add(file)
    }

    fun removeChild(file: NativeFile) {
        _children?.remove(file)
    }

    override fun delete(): NonExistingFile {
        if (parent == null) {
            throw Exception("Cannot delete root directory")
        }

        val cr = context.contentResolver
        DocumentsContract.deleteDocument(cr, uri)
        (parent.file as? RealDirectorySaf)?.removeChild(this)
        return NonExistingFileSaf(parent, name)
    }

    override fun toString(): String = uri.toString()
}

@ExperimentalStdlibApi
class NonExistingFileSaf(
        val parent: DecsyncFile,
        override val name: String
) : NonExistingFile() {
    private val asDecsyncFile = DecsyncFile(this)

    override fun child(name: String): NativeFile = NonExistingFileSaf(asDecsyncFile, name)

    override fun mkfile(): RealFile {
        val parentDir = createParentDir()
        return when (val result = parentDir.child(name)) {
            is RealFile -> result
            is RealDirectory -> throw Exception("Directory $result used as file")
            is NonExistingFile -> {
                val cr = parentDir.context.contentResolver
                val uri = DocumentsContract.createDocument(cr, parentDir.uri, "", name)
                        ?: throw Exception("Could not create file $name of parent $parentDir")
                RealFileSaf(parentDir.asDecsyncFile, parentDir.context, uri, name, 0)
                        .also { parentDir.addChild(it) }
            }
        }
    }

    private fun mkdir(): RealDirectorySaf {
        val parentDir = createParentDir()
        return when (val result = parentDir.child(name)) {
            is RealFile -> throw Exception("File $result used as directory")
            is RealDirectorySaf -> result
            is NonExistingFile -> {
                val cr = parentDir.context.contentResolver
                val uri = DocumentsContract.createDocument(cr, parentDir.uri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                        ?: throw Exception("Could not create file $name of parent $parentDir")
                RealDirectorySaf(parentDir.asDecsyncFile, parentDir.context, uri, name)
                        .also { parentDir.addChild(it) }
            }
            else -> throw Exception("Non-standard file type found. This should never happen.")
        }
    }

    private fun createParentDir(): RealDirectorySaf {
        return when (val parentNativeFile = parent.file) {
            is RealFileSaf -> throw Exception("File $parent used as directory")
            is RealDirectorySaf -> parentNativeFile
            is NonExistingFileSaf -> parentNativeFile.mkdir().also { parent.file = it }
            else -> throw Exception("Non-standard file type found. This should never happen.")
        }
    }

    override fun toString(): String = "$parent/$name"
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
    return RealDirectorySaf(null, context, uri, name)
}

fun checkUriPermissions(context: Context, uri: Uri) {
    if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED ||
            context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
        throw InsufficientAccessException()
    }
}

// Implementations using a traditional file

class RealFileSys(val file: File) : RealFile() {
    override val name = file.name

    override fun delete(): NonExistingFile {
        file.delete()
        return NonExistingFileSys(file)
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

class RealDirectorySys(val file: File) : RealDirectory() {
    override val name = file.name

    override fun child(name: String): NativeFile = nativeFileFromFile(File(file, name))

    override fun children(): List<NativeFile> =
            (file.listFiles() ?: emptyArray()).asList().map { nativeFileFromFile(it) }

    override fun delete(): NonExistingFile {
        file.delete()
        return NonExistingFileSys(file)
    }
}

class NonExistingFileSys(val file: File) : NonExistingFile() {
    override val name = file.name

    override fun child(name: String): NativeFile = NonExistingFileSys(File(file, name))

    // We only create the parent directory, the file is created automatically
    override fun mkfile(): RealFile {
        file.parentFile?.mkdirs()
        return RealFileSys(file)
    }
}

/**
 * Returns a [NativeFile] given a [file].
 */
fun nativeFileFromFile(file: File): NativeFile =
        when {
            file.isFile -> RealFileSys(file)
            file.isDirectory -> RealDirectorySys(file)
            else -> NonExistingFileSys(file)
        }