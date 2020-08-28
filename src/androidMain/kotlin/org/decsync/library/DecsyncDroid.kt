package org.decsync.library

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.provider.DocumentsContract
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlinx.serialization.json.JsonElement
import java.io.File
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

/**
 * This file contains some special functions for Android.
 * The main reason is that Android has two ways of accessing files:
 * - Using the normal [File] interface.
 * - Using SAF which uses a [Uri].
 * This file adds definitions to the normal functions given in [Decsync], but specialized to these
 * two ways of using the filesystem.
 * Furthermore, a generalization for accessing the filesystem is introduced in [NativeFile], which
 * is used in most of [Decsync]. It can be instantiated by using [nativeFileFromFile] (for a [File]
 * object) and [nativeFileFromDirUri] (for a [Uri] from SAF).
 * This makes it easier to support both ways of accessing the file system and makes it possible to
 * use the faster [File] interface when possible, but fallback to SAF using [Uri].
 */

/**
 * Instantiates a [Decsync] object using a [NativeFile]. See also [Decsync].
 */
@ExperimentalStdlibApi
fun <T> Decsync(
        decsyncDir: NativeFile,
        syncType: String,
        collection: String?,
        ownAppId: String
): Decsync<T> {
    val localDir = getDecsyncSubdir(decsyncDir, syncType, collection).child("local", ownAppId)
    return Decsync(decsyncDir, localDir, syncType, collection, ownAppId)
}

/**
 * Instantiates a [Decsync] object using a [File]. See also [Decsync].
 */
@ExperimentalStdlibApi
fun <T> Decsync(
        decsyncDir: File,
        syncType: String,
        collection: String?,
        ownAppId: String
): Decsync<T> {
    val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
    return Decsync(nativeDecsyncDir, syncType, collection, ownAppId)
}

/**
 * Instantiates a [Decsync] object using a [Uri] from SAF. See also [Decsync].
 *
 * @throws InsufficientAccessException if there is insufficient access to the [uri].
 */
@ExperimentalStdlibApi
fun <T> Decsync(
        context: Context,
        decsyncDir: Uri,
        syncType: String,
        collection: String?,
        ownAppId: String
): Decsync<T> {
    val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
    return Decsync(nativeDecsyncDir, syncType, collection, ownAppId)
}

/**
 * Variant of [checkDecsyncInfo] that uses the [File] interface.
 */
@ExperimentalStdlibApi
fun checkDecsyncInfo(
        decsyncDir: File
) {
    val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
    checkDecsyncInfo(nativeDecsyncDir)
}

/**
 * Variant of [checkDecsyncInfo] that uses a [Uri] from SAF.
 *
 * @throws InsufficientAccessException if there is insufficient access to the [uri].
 */
@ExperimentalStdlibApi
fun checkDecsyncInfo(
        context: Context,
        decsyncDir: Uri
) {
    val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
    checkDecsyncInfo(nativeDecsyncDir)
}

/**
 * Variant of [listDecsyncCollections] that uses the [File] interface.
 */
@ExperimentalStdlibApi
fun listDecsyncCollections(
        decsyncDir: File,
        syncType: String
): List<String> {
    val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
    return listDecsyncCollections(nativeDecsyncDir, syncType)
}

/**
 * Variant of [listDecsyncCollections] that uses a [Uri] from SAF.
 *
 * @throws InsufficientAccessException if there is insufficient access to the [uri].
 */
@ExperimentalStdlibApi
fun listDecsyncCollections(
        context: Context,
        decsyncDir: Uri,
        syncType: String
): List<String> {
    val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
    return listDecsyncCollections(nativeDecsyncDir, syncType)
}

/**
 * Variant of [getStaticInfo] that uses the [File] interface.
 */
@ExperimentalStdlibApi
fun Decsync.Companion.getStaticInfo(
        decsyncDir: File,
        syncType: String,
        collection: String?
): Map<JsonElement, JsonElement> {
    val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
    return getStaticInfo(nativeDecsyncDir, syncType, collection)
}

/**
 * Variant of [getStaticInfo] that uses a [Uri] from SAF.
 *
 * @throws InsufficientAccessException if there is insufficient access to the [uri].
 */
@ExperimentalStdlibApi
fun Decsync.Companion.getStaticInfo(
        context: Context,
        decsyncDir: Uri,
        syncType: String,
        collection: String?
): Map<JsonElement, JsonElement> {
    val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
    return getStaticInfo(nativeDecsyncDir, syncType, collection)
}

@ExperimentalStdlibApi
fun Decsync.Companion.getActiveApps(
        decsyncDir: File,
        syncType: String,
        collection: String?
): Pair<DecsyncVersion, List<Decsync.Companion.AppData>> {
    val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
    return getActiveApps(nativeDecsyncDir, syncType, collection)
}

@ExperimentalStdlibApi
fun Decsync.Companion.getActiveApps(
        context: Context,
        decsyncDir: Uri,
        syncType: String,
        collection: String?
): Pair<DecsyncVersion, List<Decsync.Companion.AppData>> {
    val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
    return getActiveApps(nativeDecsyncDir, syncType, collection)
}

object DecsyncPrefUtils {
    const val DECSYNC_DIRECTORY = "libdecsync.directory"
    const val CHANNEL_DECSYNC = "decsync"
    const val CHOOSE_DECSYNC_DIRECTORY = 40
    const val NOTIFICATION_ID_DELETE_APPS = 16

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
        } ?: throw Exception("Could not get name of $uri")
    }

    fun chooseDecsyncDir(fragment: Fragment) {
        val intent = getIntent(fragment.requireActivity())
        fragment.startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
    }

    fun chooseDecsyncDir(activity: Activity) {
        val intent = getIntent(activity)
        activity.startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
    }

    private fun getIntent(activity: Activity): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= 26) {
            val volumeId = "primary"
            val initialUri = getDecsyncDir(activity)
                    ?: Uri.parse("content://com.android.externalstorage.documents/document/$volumeId%3ADecSync")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        return intent
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

    @ExperimentalStdlibApi
    fun manageDecsyncData(context: Context, decsyncDir: File, syncType: String, collection: String?) {
        val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
        manageDecsyncData(context, nativeDecsyncDir, syncType, collection)
    }

    @ExperimentalStdlibApi
    fun manageDecsyncData(context: Context, decsyncDir: Uri, syncType: String, collection: String?) {
        val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
        manageDecsyncData(context, nativeDecsyncDir, syncType, collection)
    }

    @ExperimentalStdlibApi
    private fun manageDecsyncData(context: Context, decsyncDir: NativeFile, syncType: String, collection: String?) {
        val (version, appDatas) = Decsync.getActiveApps(decsyncDir, syncType, collection)
        val selectedAppDatas = mutableSetOf<Decsync.Companion.AppData>()
        AlertDialog.Builder(context)
                .setTitle("Delete DecSync apps (current version: $version)")
                .setMultiChoiceItems(appDatas.map { it.toString() }.toTypedArray(), null) { _, which, isChecked ->
                    val appData = appDatas[which]
                    if (isChecked) {
                        selectedAppDatas.add(appData)
                    } else {
                        selectedAppDatas.remove(appData)
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .setPositiveButton("Delete") { _, _ ->
                    if (selectedAppDatas.isNotEmpty()) {
                        AlertDialog.Builder(context)
                                .setTitle("Are you sure you want to delete the following ${selectedAppDatas.size} apps?")
                                .setMessage(selectedAppDatas.joinToString("\n") { it.toString() })
                                .setNegativeButton("No") { _, _ -> }
                                .setPositiveButton("Yes") { _, _ ->
                                    AsyncTask.execute {
                                        val builder = decsyncNotificationBuilder(context).apply {
                                            setSmallIcon(android.R.drawable.ic_menu_delete)
                                            setContentTitle("Deleting selected apps")
                                        }
                                        with(NotificationManagerCompat.from(context)) {
                                            notify(NOTIFICATION_ID_DELETE_APPS, builder.build())
                                        }
                                        for (appData in selectedAppDatas) {
                                            builder.setContentTitle("Deleting app ${appData.appId} (${appData.version})")
                                            with(NotificationManagerCompat.from(context)) {
                                                notify(NOTIFICATION_ID_DELETE_APPS, builder.build())
                                            }
                                            appData.delete(decsyncDir, syncType, collection)
                                        }
                                        with(NotificationManagerCompat.from(context)) {
                                            cancel(NOTIFICATION_ID_DELETE_APPS)
                                        }
                                    }
                                }
                                .show()
                    }
                }
                .show()
    }

    private fun decsyncNotificationBuilder(context: Context): NotificationCompat.Builder {
        createDecsyncNotificationChannel(context)

        return NotificationCompat.Builder(context, CHANNEL_DECSYNC).apply {
            priority = NotificationCompat.PRIORITY_LOW
            setProgress(0, 0, true)
            setOngoing(true)
        }
    }

    private fun createDecsyncNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val name = "Deleting old apps"
            val descriptionText = "Notification shown when deleting old apps"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_DECSYNC, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}