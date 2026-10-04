package app.car.parking.platform.update

import java.io.File

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** GitHub가 제공하는 자산 SHA-256. 있으면 내려받은 파일과 대조한다 */
    val sha256: String?,
    val notes: String,
)

/**
 * 스토어 밖(GitHub) 배포판의 앱 안 업데이트. 구현은 github 빌드에만 있고(src/github),
 * Play 빌드는 업데이트를 Play에 맡기므로 [SelfUpdaters.create]가 null이다(src/play).
 */
interface SelfUpdater {
    /** 최신 릴리스. 현재 버전보다 새것이 아니면 null */
    suspend fun checkLatest(): UpdateInfo?

    /** 내려받아 크기·SHA-256·패키지·버전을 확인한 파일 */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File

    /** 이 앱이 APK 설치를 요청할 수 있는지(‘출처를 알 수 없는 앱 설치’ 허용) */
    fun canInstall(): Boolean

    fun openInstallPermissionSettings()

    /** 시스템 설치 화면을 연다(설치는 사용자가 확인) */
    fun install(file: File)
}

object VersionOrder {
    /** "0.3.1" > "0.3.0". 숫자가 아닌 꼬리(-beta 등)는 무시한다 */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
