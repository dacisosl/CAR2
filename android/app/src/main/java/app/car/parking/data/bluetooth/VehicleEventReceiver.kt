package app.car.parking.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.car.parking.CarApp
import app.car.parking.platform.autolaunch.AutoLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 등록 차량의 ACL 연결/해제 수신. ACL_CONNECTED/DISCONNECTED는 매니페스트 수신기 허용 예외라
 * 앱 프로세스가 없어도 전달된다(시스템 ‘강제 중지’ 상태는 제외 — 실기기 검증 항목).
 *
 * 해제 → 기압 스냅샷 → 재연결 확인(기본 6초) → 후보 확정 → 앱 자동 표시.
 */
class VehicleEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val device: BluetoothDevice = intentDevice(intent) ?: return
        val app = context.applicationContext as CarApp
        val pending = goAsync()
        val now = System.currentTimeMillis()
        scope.launch {
            try {
                val settings = app.container.settings.current()
                val registered = settings.registeredVehicleAddress
                // 이어폰·시계 등 등록하지 않은 기기는 무시한다
                if (registered == null || !registered.equals(device.address, ignoreCase = true)) return@launch
                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Log.i(TAG, "registered vehicle connected")
                        app.container.parking.onVehicleConnected(registered, now)
                        AutoLauncher.cancelFallback(app)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        if (!settings.autoRecordRequested) return@launch
                        if (bluetoothTurningOff(app)) {
                            Log.i(TAG, "disconnect caused by Bluetooth off — not a parking event")
                            return@launch
                        }
                        handleDisconnect(app, registered, now, settings.reconnectCheckMs)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "vehicle event failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleDisconnect(app: CarApp, vehicleId: String, detectedAt: Long, checkMs: Long) {
        val container = app.container
        // 해제 직후 기압 스냅샷. 재연결 확인과 동시에 측정한다
        val pressure = scope.async { container.pressure.sample() }
        val candidate = container.parking.beginCandidate(vehicleId, detectedAt, null)
        if (candidate == null) {
            Log.i(TAG, "duplicate disconnect merged into existing candidate")
            pressure.cancel()
            return
        }
        delay(checkMs)
        val reading = pressure.await()
        if (reading != null) {
            container.db.dao().candidate(candidate.id)?.let {
                container.db.dao().upsertCandidate(it.copy(pressureHpa = reading.hpa, pressureAt = reading.measuredAtMs))
            }
        }
        if (bluetoothTurningOff(app)) {
            container.parking.cancelCandidate(candidate.id)
            return
        }
        if (!container.parking.finishReconnectCheck(candidate.id)) {
            Log.i(TAG, "reconnected within check window — candidate cancelled")
            return
        }
        val wasBackground = withContext(Dispatchers.Main) {
            !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val launched = AutoLauncher.launchCandidate(app, candidate.id, wasBackground)
        Log.i(TAG, "candidate ready, activity launch requested=$launched background=$wasBackground")
    }

    private fun bluetoothTurningOff(context: Context): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return true
        val state = adapter.state
        return state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF
    }

    @Suppress("DEPRECATION")
    private fun intentDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "VehicleEvent"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
