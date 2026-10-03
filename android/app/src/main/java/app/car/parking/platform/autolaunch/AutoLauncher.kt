package app.car.parking.platform.autolaunch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.car.parking.MainActivity
import app.car.parking.R
import app.car.parking.platform.statusbar.StatusBarNotifier

/**
 * 앱 자동 표시. 기본 경로는 Activity를 직접 띄우는 것이다.
 * Android의 백그라운드 Activity 실행 예외 중 ‘다른 앱 위에 표시(SYSTEM_ALERT_WINDOW)’ 허용 상태를 사용한다.
 * 허용되지 않은 경우에만 보조 알림을 남긴다. 알림 탭을 기본 진입 방식으로 쓰지 않는다.
 */
object AutoLauncher {
    const val EXTRA_CANDIDATE_ID = "candidateId"
    const val EXTRA_OPEN_PANEL = "openPanel"
    const val EXTRA_AUTO_ENTRY = "autoEntry"
    const val EXTRA_LAUNCH_TEST = "launchTest"
    const val EXTRA_WAS_BACKGROUND = "wasBackground"

    private const val FALLBACK_CHANNEL = "parking_candidate_v1"
    private const val FALLBACK_ID = 3

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun intent(context: Context, candidateId: String?, test: Boolean, wasBackground: Boolean): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_AUTO_ENTRY, true)
            .putExtra(EXTRA_OPEN_PANEL, !test)
            .putExtra(EXTRA_LAUNCH_TEST, test)
            .putExtra(EXTRA_WAS_BACKGROUND, wasBackground)
            .apply { if (candidateId != null) putExtra(EXTRA_CANDIDATE_ID, candidateId) }

    /** @return Activity 실행을 요청했으면 true. 실제로 화면이 떴는지는 Activity 쪽에서 기록한다. */
    fun launchCandidate(context: Context, candidateId: String, wasBackground: Boolean): Boolean {
        if (canDrawOverlays(context)) {
            val started = runCatching {
                context.startActivity(intent(context, candidateId, test = false, wasBackground = wasBackground))
            }.isSuccess
            if (started) return true
        }
        postFallback(context, candidateId)
        return false
    }

    fun launchTest(context: Context, wasBackground: Boolean): Boolean = runCatching {
        context.startActivity(intent(context, null, test = true, wasBackground = wasBackground))
    }.isSuccess

    fun cancelFallback(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(FALLBACK_ID)
    }

    /** 자동 표시 조건이 없을 때만 쓰는 보조 경로 */
    private fun postFallback(context: Context, candidateId: String) {
        if (!StatusBarNotifier.canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(FALLBACK_CHANNEL, "주차 기록 확인", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "자동 표시 설정이 없을 때 주차 기록을 확인하도록 알립니다"
                setShowBadge(false)
            }
        )
        val pending = PendingIntent.getActivity(
            context,
            FALLBACK_ID,
            intent(context, candidateId, test = false, wasBackground = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, FALLBACK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_parking)
            .setContentTitle("주차 층수를 기록하세요")
            .setContentText("자동 표시 설정이 꺼져 있어 알림으로 알려드려요")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { nm.notify(FALLBACK_ID, notification) }
    }
}
