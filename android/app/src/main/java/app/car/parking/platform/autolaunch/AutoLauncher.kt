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

    private const val FALLBACK_CHANNEL = "parking_candidate_v1"
    private const val FALLBACK_ID = 3

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun intent(context: Context, candidateId: String, animate: Boolean = true): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    // 자동 표시는 창 열림 애니메이션 없이 첫 프레임을 바로 보여 준다
                    (if (animate) 0 else Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            .putExtra(EXTRA_AUTO_ENTRY, true)
            .putExtra(EXTRA_OPEN_PANEL, true)
            .putExtra(EXTRA_CANDIDATE_ID, candidateId)

    /**
     * 기본은 화면 직접 표시. 시스템이 백그라운드 실행을 막아도 startActivity는 예외 없이 무시될 수 있어
     * 보조 알림을 함께 올리고, 화면이 실제로 열리면(openCandidate) 바로 지운다.
     * @return Activity 실행을 요청했으면 true
     */
    fun launchCandidate(context: Context, candidateId: String): Boolean {
        // 보조 알림을 먼저 올린다. 화면이 열리면(openCandidate) 지우는데, 실행 요청을 먼저 보내면
        // 이미 떠 있는 화면이 알림보다 먼저 지우기를 시도해 열린 패널 위에 알림이 남을 수 있다
        postFallback(context, candidateId)
        return canDrawOverlays(context) &&
            runCatching { context.startActivity(intent(context, candidateId, animate = false)) }.isSuccess
    }

    fun cancelFallback(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(FALLBACK_ID)
    }

    /** 보조 알림 채널. 앱 시작 때 만들어 두어 하차 순간의 실행 경로를 짧게 한다 */
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(FALLBACK_CHANNEL, "주차 기록 확인", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "자동 표시 설정이 없을 때 주차 기록을 확인하도록 알립니다"
                setShowBadge(false)
            }
        )
    }

    /** 자동 표시 조건이 없을 때만 쓰는 보조 경로 */
    private fun postFallback(context: Context, candidateId: String) {
        if (!StatusBarNotifier.canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val pending = PendingIntent.getActivity(
            context,
            FALLBACK_ID,
            intent(context, candidateId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, FALLBACK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_parking)
            .setContentTitle("주차 층수를 기록하세요")
            .setContentText("눌러서 주차 층수를 저장하세요")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { nm.notify(FALLBACK_ID, notification) }
    }
}
