package com.example.buttonremapping

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class GitHubUpdateController(
    private val activity: Activity,
    private val onStatus: (String) -> Unit = {},
) {
    private var attached = false
    private var validationInProgress = false
    private var installPromptShown = false
    private val downloadManager: DownloadManager
        get() = activity.getSystemService(DownloadManager::class.java)

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == preferences().getLong(KEY_DOWNLOAD_ID, -2L)) {
                inspectCompletedDownload(completedId)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun attach() {
        if (attached) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // DownloadManager runs in a separate system package, so Android 13+ treats
            // ACTION_DOWNLOAD_COMPLETE as an external broadcast. The stored download ID
            // is still checked before the downloaded file is queried and validated.
            activity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(downloadReceiver, filter)
        }
        attached = true
        val pendingId = preferences().getLong(KEY_DOWNLOAD_ID, -1L)
        if (pendingId >= 0L) inspectCompletedDownload(pendingId)
    }

    fun detach() {
        if (!attached) return
        runCatching { activity.unregisterReceiver(downloadReceiver) }
        attached = false
    }

    fun checkOnLaunch() {
        val lastCheck = preferences().getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - lastCheck < AUTO_CHECK_INTERVAL_MS) return
        checkForUpdates(manual = false)
    }

    fun checkNow() = checkForUpdates(manual = true)

    fun resumePendingInstallIfAllowed() {
        val prefs = preferences()
        if (!prefs.getBoolean(KEY_READY_TO_INSTALL, false)) return
        if (!activity.packageManager.canRequestPackageInstalls()) return
        val uri = prefs.getString(KEY_DOWNLOAD_URI, null)?.let(Uri::parse) ?: return
        prefs.edit().putBoolean(KEY_READY_TO_INSTALL, false).apply()
        launchInstaller(uri)
    }

    private fun checkForUpdates(manual: Boolean) {
        preferences().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        updateStatus("正在检查 GitHub 更新…")
        Thread {
            val result = runCatching { fetchLatestRelease() }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { release -> handleRelease(release, manual) }
                    .onFailure { error ->
                        RuntimeProtection.recordFailure(activity, "GitHub 更新检查失败", error)
                        updateStatus("检查失败：${error.message ?: "网络或数据异常"}")
                        if (manual) Toast.makeText(activity, "检查更新失败", Toast.LENGTH_SHORT).show()
                    }
            }
        }.start()
    }

    private fun fetchLatestRelease(): LatestRelease {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "ButtonRemappingOverlay/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub HTTP $code")
            parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: String): LatestRelease {
        val root = JSONObject(json)
        val tag = root.getString("tag_name")
        val version = UpdatePolicy.normalizedVersion(tag)
            ?: throw IllegalStateException("Release tag 不符合版本规则：$tag")
        val assetsJson = root.getJSONArray("assets")
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val item = assetsJson.getJSONObject(index)
                add(
                    ReleaseAsset(
                        name = item.getString("name"),
                        downloadUrl = item.getString("browser_download_url"),
                        size = item.getLong("size"),
                        digest = item.optString("digest"),
                    ),
                )
            }
        }
        val asset = UpdatePolicy.selectApk(version, assets)
            ?: throw IllegalStateException("Release 必须包含唯一且符合命名、网址和 SHA-256 规则的 APK")
        return LatestRelease(
            version = version,
            notes = root.optString("body").trim(),
            asset = asset,
        )
    }

    private fun handleRelease(release: LatestRelease, manual: Boolean) {
        if (!UpdatePolicy.isNewer(release.version, BuildConfig.VERSION_NAME)) {
            updateStatus("当前已是最新版（v${BuildConfig.VERSION_NAME}）")
            if (manual) Toast.makeText(activity, "当前已是最新版", Toast.LENGTH_SHORT).show()
            return
        }
        updateStatus("发现新版本 v${release.version}")
        val notes = release.notes.take(MAX_RELEASE_NOTES_CHARS).ifEmpty { "该版本未填写更新说明。" }
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 v${release.version}")
            .setMessage("当前版本：v${BuildConfig.VERSION_NAME}\n\n$notes\n\n下载后会校验哈希、包名、版本号和签名。")
            .setNegativeButton("稍后", null)
            .setPositiveButton("下载更新") { _, _ ->
                runCatching { enqueueDownload(release) }
                    .onFailure {
                        RuntimeProtection.recordFailure(activity, "启动更新下载失败", it)
                        updateStatus("无法启动更新下载")
                    }
            }
            .show()
    }

    private fun enqueueDownload(release: LatestRelease) {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("无法访问应用下载目录")
        val destination = File(directory, release.asset.name)
        if (destination.exists() && !destination.delete()) {
            updateStatus("无法替换旧的更新下载文件")
            return
        }
        val request = DownloadManager.Request(Uri.parse(release.asset.downloadUrl))
            .setTitle("游戏按钮映射 v${release.version}")
            .setDescription("正在下载更新包")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                release.asset.name,
            )
        val id = downloadManager.enqueue(request)
        preferences().edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_DOWNLOAD_PATH, destination.absolutePath)
            .putString(KEY_DOWNLOAD_VERSION, release.version)
            .putString(KEY_EXPECTED_SHA256, UpdatePolicy.expectedSha256(release.asset.digest))
            .remove(KEY_DOWNLOAD_URI)
            .putBoolean(KEY_READY_TO_INSTALL, false)
            .apply()
        updateStatus("正在下载 v${release.version}…")
        RuntimeProtection.recordEvent(activity, "开始下载 GitHub 更新", "version=${release.version}; id=$id")
    }

    private fun inspectCompletedDownload(id: Long) {
        val storedUri = preferences().getString(KEY_DOWNLOAD_URI, null)?.let(Uri::parse)
        val storedVersion = preferences().getString(KEY_DOWNLOAD_VERSION, null)
        if (storedUri != null && storedVersion != null) {
            showInstallPrompt(storedVersion, storedUri)
            return
        }
        val query = DownloadManager.Query().setFilterById(id)
        downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> validateDownload(id)
                DownloadManager.STATUS_FAILED -> {
                    updateStatus("更新包下载失败")
                    clearPendingDownload()
                }
            }
        }
    }

    private fun validateDownload(id: Long) {
        if (validationInProgress) return
        validationInProgress = true
        updateStatus("下载完成，正在校验…")
        Thread {
            val prefs = preferences()
            val file = prefs.getString(KEY_DOWNLOAD_PATH, null)?.let(::File)
                ?: return@Thread reportValidationFailure("缺少下载文件路径")
            val expectedVersion = prefs.getString(KEY_DOWNLOAD_VERSION, null)
                ?: return@Thread reportValidationFailure("缺少目标版本")
            val expectedSha = prefs.getString(KEY_EXPECTED_SHA256, null)
                ?: return@Thread reportValidationFailure("缺少 SHA-256")
            val error = validatePackage(file, expectedSha)
            if (error != null) return@Thread reportValidationFailure(error)
            val uri = downloadManager.getUriForDownloadedFile(id)
                ?: return@Thread reportValidationFailure("系统没有返回下载文件 URI")
            prefs.edit().putString(KEY_DOWNLOAD_URI, uri.toString()).apply()
            activity.runOnUiThread {
                validationInProgress = false
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                showInstallPrompt(expectedVersion, uri)
            }
        }.start()
    }

    private fun showInstallPrompt(version: String, uri: Uri) {
        if (installPromptShown || activity.isFinishing || activity.isDestroyed) return
        installPromptShown = true
        updateStatus("v$version 已下载并通过校验")
        AlertDialog.Builder(activity)
            .setTitle("更新包校验通过")
            .setMessage("v$version 已下载。继续后将打开 Android 系统安装器，安装仍需由您确认。")
            .setNegativeButton("稍后", null)
            .setPositiveButton("安装") { _, _ -> requestInstall(uri) }
            .show()
    }

    private fun validatePackage(file: File, expectedSha: String): String? {
        if (!file.isFile) return "下载文件不存在"
        val actualSha = sha256(file)
        if (!actualSha.equals(expectedSha, ignoreCase = true)) return "下载文件 SHA-256 不匹配"
        val candidate = archivePackageInfo(file) ?: return "无法读取下载 APK 信息"
        if (candidate.packageName != activity.packageName) return "下载 APK 包名不匹配"
        if (longVersionCode(candidate) <= BuildConfig.VERSION_CODE.toLong()) return "下载 APK versionCode 没有升级"
        val installed = installedPackageInfo() ?: return "无法读取当前应用签名"
        if (signerDigests(candidate) != signerDigests(installed)) return "下载 APK 签名与当前应用不一致"
        return null
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        activity.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        activity.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(Locale.ROOT, it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun requestInstall(uri: Uri) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            preferences().edit().putBoolean(KEY_READY_TO_INSTALL, true).apply()
            AlertDialog.Builder(activity)
                .setTitle("需要允许安装未知应用")
                .setMessage("Android 要求您先允许“游戏按钮映射”安装更新包。授权后返回应用将继续打开系统安装器。")
                .setNegativeButton("取消") { _, _ ->
                    preferences().edit().putBoolean(KEY_READY_TO_INSTALL, false).apply()
                }
                .setPositiveButton("去设置") { _, _ ->
                    runCatching {
                        activity.startActivity(Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"),
                        ))
                    }.onFailure {
                        preferences().edit().putBoolean(KEY_READY_TO_INSTALL, false).apply()
                        RuntimeProtection.recordFailure(activity, "打开安装未知应用设置失败", it)
                        updateStatus("无法打开安装授权设置")
                    }
                }
                .show()
            return
        }
        launchInstaller(uri)
    }

    private fun launchInstaller(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure {
                RuntimeProtection.recordFailure(activity, "打开系统安装器失败", it)
                updateStatus("无法打开系统安装器")
            }
    }

    private fun reportValidationFailure(message: String) {
        validationInProgress = false
        RuntimeProtection.recordEvent(activity, "GitHub 更新包校验失败", message)
        clearPendingDownload()
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            updateStatus("更新包校验失败：$message")
            AlertDialog.Builder(activity)
                .setTitle("更新包校验失败")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun clearPendingDownload() {
        preferences().edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_PATH)
            .remove(KEY_DOWNLOAD_VERSION)
            .remove(KEY_EXPECTED_SHA256)
            .remove(KEY_DOWNLOAD_URI)
            .remove(KEY_READY_TO_INSTALL)
            .apply()
    }

    private fun updateStatus(value: String) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) onStatus(value)
        }
    }

    private fun preferences() = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private data class LatestRelease(
        val version: String,
        val notes: String,
        val asset: ReleaseAsset,
    )

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/littletaro97-arch/ButtonRemappingOverlay/releases/latest"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val PREFS_NAME = "github_updates"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val KEY_DOWNLOAD_VERSION = "download_version"
        private const val KEY_EXPECTED_SHA256 = "expected_sha256"
        private const val KEY_DOWNLOAD_URI = "download_uri"
        private const val KEY_READY_TO_INSTALL = "ready_to_install"
        private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L
        private const val MAX_RELEASE_NOTES_CHARS = 1_200
    }
}
