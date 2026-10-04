package app.car.parking.debug

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.car.parking.BuildConfig
import app.car.parking.CarApp
import app.car.parking.data.bluetooth.VehicleEventReceiver
import kotlinx.coroutines.launch

/**
 * 디버그 빌드 전용 시뮬레이션. 실제 차량 없이 차량 등록·연결·해제 이벤트를 수신기와 같은 경로에 넣는다
 * (에뮬레이터 검증, 차 없이 폰에서 자동 표시 확인). 매니페스트에서 `android.permission.DUMP` 권한을 요구하므로
 * adb shell(시스템 권한)만 보낼 수 있고 다른 앱은 보낼 수 없다. release 빌드에는 들어가지 않는다.
 *
 *   adb shell am broadcast -a app.car.parking.debug.REGISTER -p app.car.parking --es address AA:BB:CC:DD:EE:01
 *   adb shell am broadcast -a app.car.parking.debug.DISCONNECT -p app.car.parking
 *   adb shell am broadcast -a app.car.parking.debug.CONNECT -p app.car.parking
 */
class SimulateVehicleEvent : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val app = context.applicationContext as CarApp
        val action = intent.action ?: return
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                when (action) {
                    ACTION_REGISTER -> {
                        val address = intent.getStringExtra("address") ?: return@launch
                        app.container.settings.setVehicle(address, intent.getStringExtra("name") ?: "시뮬레이션 차량")
                        Log.i(TAG, "registered simulated vehicle $address")
                    }
                    ACTION_CONNECT, ACTION_DISCONNECT -> {
                        val address = intent.getStringExtra("address")
                            ?: app.container.settings.current().registeredVehicleAddress
                            ?: return@launch
                        val real = if (action == ACTION_CONNECT) BluetoothDevice.ACTION_ACL_CONNECTED else BluetoothDevice.ACTION_ACL_DISCONNECTED
                        Log.i(TAG, "simulated ${real.substringAfterLast('.')} for $address at ${System.currentTimeMillis()}")
                        VehicleEventReceiver().handle(app, real, address, pending = null)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SimulateVehicle"
        const val ACTION_REGISTER = "app.car.parking.debug.REGISTER"
        const val ACTION_CONNECT = "app.car.parking.debug.CONNECT"
        const val ACTION_DISCONNECT = "app.car.parking.debug.DISCONNECT"
    }
}
