package org.decsync.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement
import java.io.FileNotFoundException

actual sealed class DecsyncException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidInfoException(e: Exception) : DecsyncException("Invalid .decsync-info", e)
class UnsupportedVersionException(val requiredVersion: Int, val supportedVersion: Int) : DecsyncException(
        "Unsupported DecSync version.\n" +
                "Required version: $requiredVersion.\n" +
                "Supported version: $supportedVersion.")
class InsufficientAccessException : DecsyncException("Insufficient access to the DecSync directory")

actual fun getInvalidInfoException(e: Exception): DecsyncException = InvalidInfoException(e)
actual fun getUnsupportedVersionException(requiredVersion: Int, supportedVersion: Int): DecsyncException = UnsupportedVersionException(requiredVersion, supportedVersion)

@ExperimentalStdlibApi
fun <T> getDecsync(
        context: Context,
        decsyncDir: Uri,
        syncType: String,
        collection: String?,
        ownAppId: String
): Decsync<T> {
    checkUriPermissions(context, decsyncDir)
    val nativeFile = realDirectoryFromUri(context, decsyncDir)
    return Decsync(nativeFile, syncType, collection, ownAppId)
}

@ExperimentalStdlibApi
fun checkDecsyncInfo(
        context: Context,
        decsyncDir: Uri
) {
    checkUriPermissions(context, decsyncDir)
    val nativeFile = realDirectoryFromUri(context, decsyncDir)
    checkDecsyncInfo(nativeFile)
}

@ExperimentalStdlibApi
fun listDecsyncCollections(
        context: Context,
        decsyncDir: Uri,
        syncType: String
): List<String> {
    val nativeFile = realDirectoryFromUri(context, decsyncDir)
    return listDecsyncCollections(nativeFile, syncType)
}

@ExperimentalStdlibApi
fun Decsync.Companion.getStaticInfo(
        context: Context,
        decsyncDir: Uri,
        syncType: String,
        collection: String?
): Map<JsonElement, JsonElement> {
    val nativeFile = realDirectoryFromUri(context, decsyncDir)
    return getStaticInfo(nativeFile, syncType, collection)
}

private fun checkUriPermissions(context: Context, uri: Uri) {
    if (context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED ||
            context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
        throw InsufficientAccessException()
    }
}

@ExperimentalStdlibApi
private fun realDirectoryFromUri(context: Context, uri: Uri): RealDirectoryImpl {
    val name = DecsyncPrefUtils.getNameFromUri(context, uri)
    return RealDirectoryImpl(null, context, uri, name)
}

object DecsyncPrefUtils {
    const val DECSYNC_DIRECTORY = "libdecsync.directory"

    const val CHOOSE_DECSYNC_DIRECTORY = 40

    fun getDecsyncDir(context: Context): Uri? {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(DECSYNC_DIRECTORY, null)?.let(Uri::parse)
    }

    fun putDecsyncDir(context: Context, uri: Uri) {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        editor.putString(DECSYNC_DIRECTORY, uri.toString())
        editor.apply()
    }

    fun getNameFromUri(context: Context, uri: Uri): String {
        val cr = context.contentResolver
        return cr.query(uri, arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        ), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                throw FileNotFoundException("Could find file $uri")
            }
        } ?: throw IOException("Could not get name of $uri")
    }

    fun chooseDecsyncDir(fragment: Fragment) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= 26) {
            val volumeId = "primary"
            val initialUri = getDecsyncDir(fragment.activity!!)
                    ?: Uri.parse("content://com.android.externalstorage.documents/document/$volumeId%3ADecSync")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        fragment.startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
    }

    @ExperimentalStdlibApi
    fun chooseDecsyncDirResult(context: Context, requestCode: Int, resultCode: Int, data: Intent?, callback: ((Uri) -> Unit)? = null) {
        if (requestCode != CHOOSE_DECSYNC_DIRECTORY) return
        val treeUri = data?.data
        if (resultCode == Activity.RESULT_OK && treeUri != null) {
            try {
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                val cr = context.contentResolver
                cr.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                cr.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                checkDecsyncInfo(context, uri)
                putDecsyncDir(context, uri)
                callback?.invoke(uri)
            } catch (e: DecsyncException) {
                AlertDialog.Builder(context)
                        .setTitle("DecSync")
                        .setMessage(e.message)
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
            }
        }
    }
}