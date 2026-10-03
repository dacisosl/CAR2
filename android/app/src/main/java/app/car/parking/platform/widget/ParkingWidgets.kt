package app.car.parking.platform.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.car.parking.CarApp
import app.car.parking.MainActivity
import app.car.parking.R
import app.car.parking.data.storage.LocationSource
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.Floors
import app.car.parking.platform.statusbar.FloorSign
import app.car.parking.ui.home.elapsedText
import app.car.parking.ui.theme.CarTokens
import app.car.parking.ui.theme.ElapsedTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 1×1: 층수 + 주차 경과 시간 */
class FloorWidget1x1 : BaseParkingWidget()

/** 2칸 가로: 층수 + 위치 + 주차 경과 시간 */
class FloorWidget2x1 : BaseParkingWidget()

abstract class BaseParkingWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ParkingWidgets.ACTION_TICK) refresh(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onEnabled(context: Context) = refresh(context)

    override fun onDisabled(context: Context) = refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ParkingWidgets.updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

object ParkingWidgets {
    const val ACTION_TICK = "app.car.parking.widget.TICK"
    private const val TICK_MS = 60_000L

    /** 위젯 전체를 최신 기록·테마로 다시 그린다. 기록이 있고 위젯이 있으면 1분마다 경과 시간을 갱신한다 */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext as CarApp
        val record = app.container.parking.latestRecordNow()
        val tokens = CarTokens.of(app.container.settings.current().appTheme)
        val manager = AppWidgetManager.getInstance(context)
        val small = manager.getAppWidgetIds(ComponentName(context, FloorWidget1x1::class.java))
        val wide = manager.getAppWidgetIds(ComponentName(context, FloorWidget2x1::class.java))
        val now = System.currentTimeMillis()
        small.forEach { manager.updateAppWidget(it, render(context, R.layout.widget_1x1, record, tokens, now)) }
        wide.forEach { manager.updateAppWidget(it, render(context, R.layout.widget_2x1, record, tokens, now)) }
        scheduleTick(context, enabled = record != null && (small.isNotEmpty() || wide.isNotEmpty()))
    }

    private fun render(context: Context, layout: Int, record: ParkingRecordEntity?, t: CarTokens, now: Long): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        val floor = record?.let { Floors.label(it.floorLevel) } ?: "—"
        val elapsedMs = record?.let { now - it.detectedAt }
        val elapsed = elapsedMs?.let { elapsedText(it) } ?: "기록 없음"
        val elapsedColor = elapsedMs?.let { ElapsedTone.color(it, t.black) } ?: t.textSecondary

        views.setInt(R.id.widget_bg, "setColorFilter", t.white.toArgb())
        views.setTextViewText(R.id.widget_floor, floor)
        // 층 표지판: 상태바 아이콘과 같은 색 규칙
        val sign = FloorSign.of(record?.floorLevel)
        views.setInt(R.id.widget_block, "setColorFilter", sign.plate)
        views.setTextColor(R.id.widget_floor, sign.text)
        views.setTextViewText(R.id.widget_elapsed, elapsed)
        views.setTextColor(R.id.widget_elapsed, elapsedColor.toArgb())
        if (layout == R.layout.widget_1x1) {
            views.setContentDescription(R.id.widget_root, "주차 층수 $floor, 주차한 지 $elapsed")
        } else {
            val location = record?.let(::locationLine) ?: "층수를 기록하면 표시돼요"
            views.setTextViewText(R.id.widget_location, location)
            views.setTextColor(R.id.widget_location, t.textSecondary.toArgb())
            views.setContentDescription(R.id.widget_root, "주차 층수 $floor, $location, 주차한 지 $elapsed")
        }
        val open = PendingIntent.getActivity(
            context,
            layout,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    private fun locationLine(record: ParkingRecordEntity): String {
        record.placeName?.let { return it }
        if (record.latitude == null) return "위치 없음"
        return when (record.locationSource) {
            LocationSource.SAVED -> "직접 저장한 위치"
            LocationSource.AFTER_DISCONNECT -> "하차 직후 위치"
            LocationSource.MANUAL -> "기록 시 위치"
            else -> "저장된 위치"
        }
    }

    /**
     * 경과 시간 갱신. 화면이 꺼져 있을 때는 깨우지 않는(ELAPSED_REALTIME) 반복 알람이라
     * 화면을 켤 때 밀린 갱신이 한 번 실행된다.
     */
    private fun scheduleTick(context: Context, enabled: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, FloorWidget1x1::class.java).setAction(ACTION_TICK)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarm.cancel(pending)
        if (enabled) {
            alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + TICK_MS, TICK_MS, pending)
        }
    }
}
