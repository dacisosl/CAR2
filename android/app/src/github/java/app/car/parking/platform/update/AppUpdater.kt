package app.car.parking.platform.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.car.parking.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** GitHub 배포판의 업데이트 구현. Play 빌드에는 이 파일이 들어가지 않는다(src/play의 SelfUpdaters는 null) */
object SelfUpdaters {
    fun create(context: Context): SelfUpdater? = AppUpdater(context)
}

/**
 * GitHub 릴리스 기반 업데이트 확인. 파일로 설치한 앱은 스스로 업데이트되지 않으므로
 * 최신 릴리스를 확인해 APK를 내려받고 시스템 설치 화면을 연다(설치는 사용자가 확인).
 */
class AppUpdater(private val context: Context) : SelfUpdater {

    /** 최신 릴리스. 현재 버전보다 새것이 아니면 null */
    override suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            if (!VersionOrder.isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            UpdateInfo(
                versionName = version,
                apkUrl = apk.getString("browser_download_url"),
                sizeBytes = apk.optLong("size"),
                sha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
                notes = json.optString("body"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** 앱 캐시에 내려받는다. 크기·SHA-256·패키지 이름·버전이 맞지 않으면 버린다 */
    override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "CAR-parking-${info.versionName}.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var read = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && !info.sha256.equals(hash, ignoreCase = true)) {
            file.delete()
            error("파일 확인(SHA-256) 실패")
        }
        verifyPackage(file, info)
        file
    }

    private fun verifyPackage(file: File, info: UpdateInfo) {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (archive == null || archive.packageName != context.packageName || archive.versionName != info.versionName) {
            file.delete()
            error("이 앱의 업데이트 파일이 아니에요")
        }
    }

    /** 이 앱이 APK 설치를 요청할 수 있는지(‘출처를 알 수 없는 앱 설치’ 허용) */
    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 시스템 설치 화면을 연다. 서명이 다르면 시스템이 설치를 거부한다 */
    override fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/dacisosl/CAR2/releases/latest"
    }
}
