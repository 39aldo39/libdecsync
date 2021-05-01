package org.decsync.library

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.work.*
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
@RequiresApi(21)
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
@RequiresApi(21)
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
@RequiresApi(21)
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
@RequiresApi(21)
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
@RequiresApi(21)
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

    @RequiresApi(21)
    fun getDecsyncDir(context: Context): Uri? {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(DECSYNC_DIRECTORY, null)?.let(Uri::parse)
    }

    @RequiresApi(21)
    fun putDecsyncDir(context: Context, uri: Uri) {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        editor.putString(DECSYNC_DIRECTORY, uri.toString())
        editor.apply()
    }

    @RequiresApi(21)
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

    @RequiresApi(21)
    fun chooseDecsyncDir(fragment: Fragment) {
        val intent = getIntent(fragment.requireActivity())
        fragment.startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
    }

    @RequiresApi(21)
    fun chooseDecsyncDir(activity: Activity) {
        val intent = getIntent(activity)
        activity.startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
    }

    @RequiresApi(21)
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
    @RequiresApi(21)
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
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
            }
        }
    }

    @ExperimentalStdlibApi
    fun manageDecsyncData(context: Context, decsyncDir: File, syncType: String, collection: String?, params: Params = Params()) {
        val nativeDecsyncDir = nativeFileFromFile(decsyncDir)
        manageDecsyncData(context, nativeDecsyncDir, syncType, collection, params)
    }

    @ExperimentalStdlibApi
    @RequiresApi(21)
    fun manageDecsyncData(context: Context, decsyncDir: Uri, syncType: String, collection: String?, params: Params = Params()) {
        val nativeDecsyncDir = nativeFileFromDirUri(context, decsyncDir)
        manageDecsyncData(context, nativeDecsyncDir, syncType, collection, params)
    }

    class Params(
            val ownAppId: String? = null,
            val colorNormal: Int = Color.GRAY,
            val colorRed: Int = Color.rgb(0xD5, 0x00, 0x00),
            val colorOrange: Int = Color.rgb(0xFF, 0x98, 0x00),
            val colorGreen: Int = Color.rgb(0x4C, 0xAF, 0x50)
    )

    @ExperimentalStdlibApi
    fun manageDecsyncData(context: Context, decsyncDir: NativeFile, syncType: String, collection: String?, params: Params = Params()) {
        val (currentVersion, appDatas) = Decsync.getActiveApps(decsyncDir, syncType, collection)
        val selectedAppDatas = mutableSetOf<Decsync.Companion.AppData>()
        val listView = ListView(context)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        val adapter = AppDataAdapter(context, selectedAppDatas, currentVersion, params, false)
        adapter.add(null)
        adapter.addAll(appDatas)
        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, view, position, _ ->
            if (position == 0 || !view.isEnabled) return@OnItemClickListener
            val appData = appDatas[position-1]
            if (selectedAppDatas.contains(appData)) {
                selectedAppDatas.remove(appData)
            } else {
                selectedAppDatas.add(appData)
            }
            adapter.notifyDataSetChanged()
        }
        AlertDialog.Builder(context)
                .setTitle(R.string.delete_apps_title)
                .setView(listView)
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setPositiveButton(R.string.delete) { _, _ ->
                    if (selectedAppDatas.isNotEmpty()) {
                        val listViewOverview = ListView(context)
                        val adapterOverview = AppDataAdapter(context, selectedAppDatas, currentVersion, params, true)
                        adapterOverview.add(null)
                        adapterOverview.addAll(selectedAppDatas)
                        listViewOverview.adapter = adapterOverview
                        AlertDialog.Builder(context)
                                .setTitle(context.getString(R.string.delete_apps_confirm_title, selectedAppDatas.size))
                                .setView(listViewOverview)
                                .setNegativeButton(android.R.string.no) { _, _ -> }
                                .setPositiveButton(android.R.string.yes) deleteApps@{ _, _ ->
                                    val (useSaf, decsyncDirString) = when (val node = decsyncDir.fileSystemNode) {
                                        is RealDirectorySys -> Pair(false, node.file.path)
                                        is RealDirectorySaf -> Pair(true, node.uri.toString())
                                        else -> {
                                            Log.w("Cannot remove apps in DecSync dir $decsyncDir. Not a directory.")
                                            return@deleteApps
                                        }
                                    }
                                    val appIds = selectedAppDatas.map { it.appId }.toTypedArray()
                                    val versionsInt = selectedAppDatas.map { it.version.toInt() }.toIntArray()
                                    val inputData = Data.Builder()
                                            .putBoolean(DeleteWorker.KEY_USE_SAF, useSaf)
                                            .putString(DeleteWorker.KEY_DECSYNC_DIR, decsyncDirString)
                                            .putString(DeleteWorker.KEY_SYNC_TYPE, syncType)
                                            .putString(DeleteWorker.KEY_COLLECTION, collection)
                                            .putInt(DeleteWorker.KEY_SIZE, selectedAppDatas.size)
                                            .putStringArray(DeleteWorker.KEY_APP_IDS, appIds)
                                            .putIntArray(DeleteWorker.KEY_VERSIONS, versionsInt)
                                            .putInt(DeleteWorker.KEY_CURRENT_VERSION, currentVersion.toInt())
                                            .build()
                                    val workRequest = OneTimeWorkRequest.Builder(DeleteWorker::class.java)
                                            .setInputData(inputData)
                                            .build()
                                    val uniqueName = "$decsyncDirString-$syncType-$collection"
                                    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
                                }
                                .show()
                    }
                }
                .show()
    }

    @ExperimentalStdlibApi
    fun permDeleteCollectionUsingWorker(context: Context, decsyncDir: NativeFile, syncType: String, collection: String?) {
        val (useSaf, decsyncDirString) = when (val node = decsyncDir.fileSystemNode) {
            is RealDirectorySys -> Pair(false, node.file.path)
            is RealDirectorySaf -> Pair(true, node.uri.toString())
            else -> {
                Log.w("Cannot remove data in DecSync dir $decsyncDir. Not a directory.")
                return
            }
        }
        val inputData = Data.Builder()
                .putBoolean(DeleteWorker.KEY_USE_SAF, useSaf)
                .putString(DeleteWorker.KEY_DECSYNC_DIR, decsyncDirString)
                .putString(DeleteWorker.KEY_SYNC_TYPE, syncType)
                .putString(DeleteWorker.KEY_COLLECTION, collection)
                .build()
        val workRequest = OneTimeWorkRequest.Builder(PermDeleteWorker::class.java)
                .setInputData(inputData)
                .build()
        val uniqueName = "$decsyncDirString-$syncType-$collection"
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
    }
}

@ExperimentalStdlibApi
class AppDataAdapter(
        context: Context,
        private val selectedAppDatas: MutableSet<Decsync.Companion.AppData>,
        private val currentVersion: DecsyncVersion,
        private val params: DecsyncPrefUtils.Params,
        private val isOverview: Boolean
): ArrayAdapter<Decsync.Companion.AppData?>(context, R.layout.app_data_item) {
    override fun getView(position: Int, v: View?, parent: ViewGroup): View {
        val v = v ?: LayoutInflater.from(context).inflate(R.layout.app_data_item, parent, false)
        val appData = getItem(position) ?: Decsync.Companion.AppData(
                context.getString(R.string.app_id),
                context.getString(R.string.last_active),
                currentVersion)
        val isOwnApp = isOwnApp(position, appData)
        val isEnabled = isEnabled(position, appData)

        v.isEnabled = isEnabled

        val checked = v.findViewById<CheckBox>(R.id.checked)
        checked.isChecked = selectedAppDatas.contains(appData)
        checked.isEnabled = isEnabled
        checked.visibility = when {
            isOverview -> View.GONE
            position == 0 -> View.INVISIBLE
            else -> View.VISIBLE
        }

        val appIdView = v.findViewById<TextView>(R.id.app_id)
        appIdView.text = appData.appId
        appIdView.typeface = if (position == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextViewColor(appIdView, if (isOwnApp) params.colorGreen else params.colorNormal, isOwnApp)

        val versionView = v.findViewById<TextView>(R.id.version)
        versionView.text = appData.version.toString()
        versionView.typeface = if (position == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextViewColor(versionView, if (appData.version < currentVersion) params.colorRed else params.colorNormal, isOwnApp)

        val lastActiveView = v.findViewById<TextView>(R.id.last_active)
        lastActiveView.text = appData.lastActive ?: context.getString(R.string.last_active_unknown)
        lastActiveView.typeface = if (position == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextViewColor(lastActiveView, when {
            position == 0 -> params.colorNormal
            appData.lastActive == null -> params.colorOrange
            appData.lastActive < oldDatetime() -> params.colorRed
            else -> params.colorGreen
        }, isOwnApp)

        return v
    }

    override fun isEnabled(position: Int): Boolean {
        val appData = getItem(position) ?: return false
        return isEnabled(position, appData)
    }

    private fun isEnabled(position: Int, appData: Decsync.Companion.AppData): Boolean {
        val isOwnApp = isOwnApp(position, appData)
        return !isOverview && position != 0 && !isOwnApp
    }

    private fun isOwnApp(position: Int, appData: Decsync.Companion.AppData): Boolean {
        return position != 0 && appData.appId == params.ownAppId && appData.version == currentVersion
    }

    private fun setTextViewColor(v: TextView, color: Int, isTrans: Boolean) {
        val colorTrans = if (isTrans)
            ColorUtils.setAlphaComponent(color, Color.alpha(color) / 2)
        else
            color
        v.setTextColor(colorTrans)
    }
}

@ExperimentalStdlibApi
class DeleteWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val useSaf = inputData.getBoolean(KEY_USE_SAF, false)
        val decsyncDirString = inputData.getString(KEY_DECSYNC_DIR) ?: return Result.failure()
        val decsyncDir = if (useSaf) {
            if (Build.VERSION.SDK_INT < 21) {
                return Result.failure()
            }
            nativeFileFromDirUri(context, Uri.parse(decsyncDirString))
        } else {
            nativeFileFromFile(File(decsyncDirString))
        }
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: return Result.failure()
        val collection = inputData.getString(KEY_COLLECTION)
        val size = inputData.getInt(KEY_SIZE, -1)
        val appIds = inputData.getStringArray(KEY_APP_IDS) ?: return Result.failure()
        if (appIds.size != size) return Result.failure()
        val versions = inputData.getIntArray(KEY_VERSIONS)?.map { versionInt ->
            DecsyncVersion.fromInt(versionInt) ?: return Result.failure()
        } ?: return Result.failure()
        if (versions.size != size) return Result.failure()
        val currentVersion = DecsyncVersion.fromInt(inputData.getInt(KEY_CURRENT_VERSION, -1)) ?: return Result.failure()

        Log.i("Deleting $size apps in $decsyncDirString/$syncType/$collection")
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val notification = deleteNotificationBuilder(context).apply {
            setSmallIcon(android.R.drawable.ic_menu_delete)
            setContentTitle(context.getString(R.string.notification_delete_title_all))
            addAction(android.R.drawable.ic_delete, context.getString(android.R.string.cancel), cancelIntent)
        }
        val notificationId = id.hashCode()
        setForeground(ForegroundInfo(notificationId, notification.build()))

        for (i in 0 until size) {
            if (isStopped) return Result.success()
            val appId = appIds[i]
            val version = versions[i]
            Log.i("Deleting app $appId ($version)")
            notification.apply {
                setProgress(size, i, false)
                setContentTitle(context.getString(R.string.notification_delete_title, appId, version))
            }
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification.build())
            }
            Decsync.deleteAppData(decsyncDir, syncType, collection, appId, version, currentVersion)
        }

        return Result.success()
    }

    companion object {
        const val KEY_USE_SAF = "KEY_USE_SAF"
        const val KEY_DECSYNC_DIR = "KEY_DECSYNC_DIR"
        const val KEY_SYNC_TYPE = "KEY_SYNC_TYPE"
        const val KEY_COLLECTION = "KEY_COLLECTION"
        const val KEY_SIZE = "KEY_SIZE"
        const val KEY_APP_IDS = "KEY_APP_ID"
        const val KEY_VERSIONS = "KEY_VERSION"
        const val KEY_CURRENT_VERSION = "KEY_CURRENT_VERSION"

        fun deleteNotificationBuilder(context: Context): NotificationCompat.Builder {
            createDeleteNotificationChannel(context)

            return NotificationCompat.Builder(context, DecsyncPrefUtils.CHANNEL_DECSYNC).apply {
                priority = NotificationCompat.PRIORITY_LOW
                setProgress(0, 0, true)
                setOngoing(true)
            }
        }

        private fun createDeleteNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val name = context.getString(R.string.notification_channel_delete_name)
                val descriptionText = context.getString(R.string.notification_channel_delete_desc)
                val importance = NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel(DecsyncPrefUtils.CHANNEL_DECSYNC, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}

@ExperimentalStdlibApi
class PermDeleteWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val useSaf = inputData.getBoolean(DeleteWorker.KEY_USE_SAF, false)
        val decsyncDirString = inputData.getString(DeleteWorker.KEY_DECSYNC_DIR) ?: return Result.failure()
        val decsyncDir = if (useSaf) {
            if (Build.VERSION.SDK_INT < 21) {
                return Result.failure()
            }
            nativeFileFromDirUri(context, Uri.parse(decsyncDirString))
        } else {
            nativeFileFromFile(File(decsyncDirString))
        }
        val syncType = inputData.getString(DeleteWorker.KEY_SYNC_TYPE) ?: return Result.failure()
        val collection = inputData.getString(DeleteWorker.KEY_COLLECTION)

        Log.i("Permanently deleting $decsyncDirString/$syncType/$collection")
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val notification = DeleteWorker.deleteNotificationBuilder(context).apply {
            setSmallIcon(android.R.drawable.ic_menu_delete)
            setContentTitle(context.getString(R.string.notification_perm_delete_title, collection))
            addAction(android.R.drawable.ic_delete, context.getString(android.R.string.cancel), cancelIntent)
        }
        val notificationId = id.hashCode()
        setForeground(ForegroundInfo(notificationId, notification.build()))

        Decsync.permDeleteCollection(decsyncDir, syncType, collection)

        return Result.success()
    }
}