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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 앱 자동 표시. 기본 경로는 Activity를 직접 띄우는 것이다.
 * Android의 백그라운드 Activity 실행 예외 중 ‘다른 앱 위에 표시(SYSTEM_ALERT_WINDOW)’ 허용 상태를 사용한다.
 * 허용되지 않은 경우에만 보조 알림을 남긴다. 알림 탭을 기본 진입 방식으로 쓰지 않는다.
 */
object AutoLauncher {
    const val EXTRA_CANDIDATE_ID = "candidateId"
    const val EXTRA_OPEN_PANEL = "openPanel"
    const val EXTRA_AUTO_ENTRY = "autoEntry"
    /** 보조 알림을 눌러 연 경우. 사용자가 직접 연 것이므로 자동 진입 취소 때 뒤로 보내지 않는다 */
    const val EXTRA_VIA_NOTIFICATION = "viaNotification"

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

    /** 화면이 실제로 연 후보(같은 프로세스). [markShown]이 기록하고 [awaitShown]이 기다린다 */
    @Volatile
    private var shownCandidateId: String? = null

    /**
     * 기본은 화면 직접 표시. 시스템이 백그라운드 실행을 조용히 막을 수 있으므로, 호출한 쪽은
     * [awaitShown]으로 화면이 실제로 열렸는지 확인하고 열리지 않았을 때만 [postFallback]으로 보조 알림을 올린다.
     * 정상 경로에서는 알림 헤드업·소리가 나지 않는다.
     * @return Activity 실행을 요청했으면 true
     */
    fun launchCandidate(context: Context, candidateId: String): Boolean =
        canDrawOverlays(context) &&
            runCatching { context.startActivity(intent(context, candidateId, animate = false)) }.isSuccess

    /** 화면(openCandidate)이 이 후보의 패널을 열었다. 이미 올라간 보조 알림도 지운다 */
    fun markShown(context: Context, candidateId: String) {
        shownCandidateId = candidateId
        cancelFallback(context)
    }

    /** 화면이 이 후보를 열 때까지 최대 [timeoutMs] 기다린다. 열렸으면 true */
    suspend fun awaitShown(candidateId: String, timeoutMs: Long): Boolean {
        if (shownCandidateId == candidateId) return true
        if (timeoutMs <= 0L) return false
        return withTimeoutOrNull(timeoutMs) {
            while (shownCandidateId != candidateId) delay(SHOWN_POLL_MS)
            true
        } ?: false
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

    /** 자동 표시가 되지 않았을 때만 쓰는 보조 경로 */
    fun postFallback(context: Context, candidateId: String) {
        if (!StatusBarNotifier.canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val pending = PendingIntent.getActivity(
            context,
            FALLBACK_ID,
            intent(context, candidateId).putExtra(EXTRA_VIA_NOTIFICATION, true),
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
        // 알림을 올리는 사이 화면이 열렸으면 바로 지운다(화면 쪽 지우기가 알림보다 먼저 끝났을 수 있다)
        if (shownCandidateId == candidateId) cancelFallback(context)
    }

    private const val SHOWN_POLL_MS = 50L
}
