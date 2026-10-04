package app.car.parking.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.car.parking.CarApp
import app.car.parking.platform.statusbar.StatusBarNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 재부팅·앱 업데이트 때 시스템이 알림을 지우므로 상태바 층 표시를 복원한다.
 * 재부팅 뒤에는 차량 연결 상태를 알 수 없으므로 ‘연결 안 됨’으로 되돌린다(다음 ACL 이벤트·앱 열기에서 갱신).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as CarApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) app.container.settings.setVehicleLink(connected = false)
                StatusBarNotifier.sync(app, app.container.parking.latestRecordNow())
            } finally {
                pending.finish()
            }
        }
    }
}
