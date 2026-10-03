package app.car.parking.platform.statusbar

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.car.parking.MainActivity
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.Floors

/**
 * 상태바 층 표지판. CARwhere v5.9의 StatusBarNotifier를 이 앱에 맞게 옮겼다.
 *
 *  - 상태바에 [B2] 같은 층 표지판 아이콘(글자를 뚫어낸 표지판)을 띄운다.
 *  - 알림창에는 층·경과 시간을 표시한다. 탭하면 홈이 열린다(자동 표시 진입 경로와는 별개).
 *  - Android 16: Live Updates 승격을 요청해 상태바 칩·잠금화면에 표시한다.
 *  - 일반 상시 알림이라 서비스가 종료돼도 남는다. 재부팅·업데이트 후에는 BootReceiver가 다시 게시한다.
 *  - 층수를 기록할 때 패널의 상태바 스위치로 기록마다 켜고 끈다(집 근처 기본 켜짐, 그 외 기본 꺼짐).
 */
object StatusBarNotifier {

    const val NOTIFICATION_ID = 2

    // 채널 중요도는 만든 뒤 앱에서 바꿀 수 없다. 바꾸려면 id를 올리고 이전 채널을 지운다.
    private const val CHANNEL_ID = "parked_floor_v1"

    /** 층을 모를 때(P) 표지판 색 */
    const val UNKNOWN_ARGB: Int = 0xFF2F6B4F.toInt()

    // 어두운 상태바에서 색만 보고도 층을 구분하도록 명도가 아니라 색상으로 나눈다
    private val BASEMENT = intArrayOf(
        0xFFC6FF00.toInt(), // B1 라임
        0xFF76FF03.toInt(), // B2 연두
        0xFF00E676.toInt(), // B3 초록
        0xFF00E5FF.toInt(), // B4 시안
    )
    private val GROUND = intArrayOf(
        0xFFFFD54F.toInt(), // 1F 앰버
        0xFFFFAB40.toInt(), // 2F 오렌지
        0xFFFF7043.toInt(), // 3F 진한 주황
        0xFFFF5252.toInt(), // 4F 코랄
    )

    fun floorColor(floor: String?): Int {
        if (floor.isNullOrBlank()) return UNKNOWN_ARGB
        val number = floor.filter { it.isDigit() }.toIntOrNull() ?: return UNKNOWN_ARGB
        val tones = if (floor.startsWith("B")) BASEMENT else GROUND
        return tones[(number - 1).coerceIn(0, tones.size - 1)]
    }

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            // MIN이면 상태바 아이콘이 뜨지 않고 Live Updates 승격도 안 된다 → DEFAULT + 무음
            NotificationChannel(CHANNEL_ID, "상태바 층 표시", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "확정한 주차 층수를 상태바에 표시합니다"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 최신 기록의 상태바 선택에 맞춰 표시하거나 지운다. */
    fun sync(context: Context, record: ParkingRecordEntity?) {
        if (record == null || !record.statusBarShown) {
            dismiss(context)
            return
        }
        val floor = Floors.label(record.floorLevel)
        show(context, floor, floor?.let { "$it · 주차 위치" } ?: "층수 미선택", record.detectedAt)
    }

    fun show(context: Context, floor: String?, detail: String, startedAtMs: Long) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { nm.notify(NOTIFICATION_ID, build(context, floor, detail, startedAtMs, open)) }
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun build(
        context: Context,
        floor: String?,
        detail: String,
        startedAtMs: Long,
        contentIntent: PendingIntent,
    ): Notification {
        val tone = floorColor(floor)
        val icon = IconCompat.createWithBitmap(renderSignIcon(floor ?: "P", tone))
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentIntent(contentIntent)
            .setContentTitle(if (floor != null) "$floor 에 주차됨" else "주차 위치 저장됨")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setColor(tone)
            .setOngoing(true)
            .setShowWhen(startedAtMs > 0L)
            .setWhen(if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis())
            .setUsesChronometer(startedAtMs > 0L)
            .setSubText(if (floor != null) "P·$floor" else "P")
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(floor ?: "P")
            .build()
    }

    enum class LiveUpdatesState { Unsupported, Disabled, Ready }

    fun liveUpdatesState(context: Context): LiveUpdatesState {
        if (Build.VERSION.SDK_INT < 36) return LiveUpdatesState.Unsupported
        val nm = context.getSystemService(NotificationManager::class.java)
        val allowed = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(true)
        return if (allowed) LiveUpdatesState.Ready else LiveUpdatesState.Disabled
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 둥근 사각 표지판 + 글자를 투명하게 뚫어낸 비트맵.
     * 순정 Android는 알파 실루엣만 단색으로 그리고 일부 제조사는 색을 보존한다. 양쪽 모두 글자가 읽힌다.
     */
    fun renderSignIcon(text: String, tint: Int = UNKNOWN_ARGB): Bitmap {
        val size = 144
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = 3f
        val top = inset
        val bottom = size - inset
        val corner = (bottom - top) * 0.22f
        canvas.drawRoundRect(0f, top, size.toFloat(), bottom, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint })

        val punch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val bounds = Rect()
        punch.textSize = 100f
        punch.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            punch.textSize = 100f * minOf((bottom - top) * 0.74f / bounds.height(), size * 0.84f / bounds.width())
            punch.getTextBounds(text, 0, text.length, bounds)
        }
        val baseline = (top + bottom) / 2f - (bounds.top + bounds.bottom) / 2f
        canvas.drawText(text, size / 2f, baseline, punch)
        return bitmap
    }
}
