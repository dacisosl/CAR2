package app.car.parking.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.car.parking.CarApp
import app.car.parking.platform.statusbar.StatusBarNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 재부팅·앱 업데이트 때 시스템이 알림을 지우므로 상태바 층 표시를 복원한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as CarApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = app.container.settings.current()
                StatusBarNotifier.sync(app, settings.statusBarEnabled, app.container.parking.latestRecordNow())
            } finally {
                pending.finish()
            }
        }
    }
}
