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
 *  - 상태바에 [B2] 같은 층 표지판 아이콘을 띄운다. 색 규칙은 [FloorSign](위젯과 같음).
 *  - 알림창에는 층·경과 시간을 표시한다. 탭하면 홈이 열린다(자동 표시 진입 경로와는 별개).
 *  - Android 16: Live Updates 승격을 요청해 상태바 칩·잠금화면에 표시한다.
 *  - 일반 상시 알림이라 서비스가 종료돼도 남는다. 재부팅·업데이트 후에는 BootReceiver가 다시 게시한다.
 *  - 층수를 기록할 때 패널의 상태바 스위치로 기록마다 켜고 끈다(집 근처 기본 켜짐, 그 외 기본 꺼짐).
 */
object StatusBarNotifier {

    const val NOTIFICATION_ID = 2

    // 채널 중요도는 만든 뒤 앱에서 바꿀 수 없다. 바꾸려면 id를 올리고 이전 채널을 지운다.
    private const val CHANNEL_ID = "parked_floor_v1"

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
        val sign = FloorSign.of(floor)
        val icon = IconCompat.createWithBitmap(renderSignIcon(floor ?: "P", sign))
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentIntent(contentIntent)
            .setContentTitle(if (floor != null) "$floor 에 주차됨" else "주차 위치 저장됨")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setColor(sign.accent)
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
     * 둥근 사각 표지판 위에 색 글자를 그린다. 글자 둘레는 투명하게 한 줄 비운다.
     * 삼성 등 색을 보존하는 기기에서는 표지판·글자 색이 그대로 보이고, 순정 Android처럼
     * 아이콘을 한 가지 색 실루엣으로 바꾸는 기기에서도 비운 테두리 덕분에 글자 모양이 읽힌다.
     */
    fun renderSignIcon(text: String, sign: FloorSign): Bitmap {
        val size = 144
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = 3f
        val top = inset
        val bottom = size - inset
        val corner = (bottom - top) * 0.22f
        val edge = 5f
        val rect = android.graphics.RectF(0f, top, size.toFloat(), bottom)
        canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sign.edge })
        rect.inset(edge, edge)
        canvas.drawRoundRect(rect, corner - edge, corner - edge, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sign.plate })

        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        glyph.textSize = 100f
        glyph.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            glyph.textSize = 100f * minOf((bottom - top) * 0.62f / bounds.height(), size * 0.72f / bounds.width())
            glyph.getTextBounds(text, 0, text.length, bounds)
        }
        val baseline = (top + bottom) / 2f - (bounds.top + bounds.bottom) / 2f
        // 1) 글자 둘레를 투명하게 비운다
        val gap = Paint(glyph).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawText(text, size / 2f, baseline, gap)
        // 2) 글자를 색으로 채운다
        canvas.drawText(text, size / 2f, baseline, Paint(glyph).apply { color = sign.text })
        return bitmap
    }
}
