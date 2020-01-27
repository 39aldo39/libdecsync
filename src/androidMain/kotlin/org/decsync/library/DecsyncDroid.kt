package org.decsync.library

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlinx.serialization.json.JsonElement

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
        decsyncDir: DocumentFile,
        cr: ContentResolver,
        syncType: String,
        collection: String?,
        ownAppId: String
): Decsync<T> {
    if (!decsyncDir.canRead() || !decsyncDir.canWrite()) {
        throw InsufficientAccessException()
    }
    val nativeFile = RealDirectoryImpl(decsyncDir)
    return Decsync(nativeFile, cr, syncType, collection, ownAppId)
}

@ExperimentalStdlibApi
fun checkDecsyncInfo(
        decsyncDir: DocumentFile,
        cr: ContentResolver
) {
    if (!decsyncDir.canRead() || !decsyncDir.canWrite()) {
        throw InsufficientAccessException()
    }
    val nativeFile = RealDirectoryImpl(decsyncDir)
    checkDecsyncInfo(nativeFile, cr)
}

@ExperimentalStdlibApi
fun listDecsyncCollections(
        decsyncDir: DocumentFile,
        syncType: String
): List<String> {
    val nativeFile = RealDirectoryImpl(decsyncDir)
    return listDecsyncCollections(nativeFile, syncType)
}

@ExperimentalStdlibApi
fun Decsync.Companion.getStaticInfo(
        decsyncDir: DocumentFile,
        cr: ContentResolver,
        syncType: String,
        collection: String?
): Map<JsonElement, JsonElement> {
    val nativeFile = RealDirectoryImpl(decsyncDir)
    return getStaticInfo(nativeFile, cr, syncType, collection)
}

object DecsyncPrefUtils {
    const val DECSYNC_DIRECTORY = "libdecsync.directory"

    const val CHOOSE_DECSYNC_DIRECTORY = 40

    fun getDecsyncDir(context: Context): DocumentFile? {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val uri = settings.getString(DECSYNC_DIRECTORY, null)?.let(Uri::parse) ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun putDecsyncDir(context: Context, file: DocumentFile) {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        editor.putString(DECSYNC_DIRECTORY, file.uri.toString())
        editor.apply()
    }

    fun chooseDecsyncDir(fragment: Fragment) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= 26) {
            val volumeId = "primary"
            val initialUri = getDecsyncDir(fragment.activity!!)?.uri
                    ?: Uri.parse("content://com.android.externalstorage.documents/document/$volumeId%3ADecSync")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        fragment.startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
    }

    @ExperimentalStdlibApi
    fun chooseDecsyncDirResult(context: Context, requestCode: Int, resultCode: Int, data: Intent?, callback: ((DocumentFile) -> Unit)? = null) {
        if (requestCode != CHOOSE_DECSYNC_DIRECTORY) return
        val uri = data?.data
        if (resultCode == Activity.RESULT_OK && uri != null) {
            try {
                val cr = context.contentResolver
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val dir = DocumentFile.fromTreeUri(context, uri)!!
                checkDecsyncInfo(dir, cr)
                putDecsyncDir(context, dir)
                callback?.invoke(dir)
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