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
 * 연결 → ‘이동 중’ 상태 저장, 확인 중이던 후보 취소.
 * 해제 → 후보 생성(‘하차 감지’) → 기압 스냅샷 → 재연결 확인(기본 2초) → 실제 연결 여부 조회 → 앱 자동 표시.
 *
 * 재연결 확인 동안 수신기를 goAsync로 붙잡아 둔다. 수신기를 먼저 끝내면 백그라운드 프로세스가
 * 몇 초 안에 정리될 수 있어 확인이 끝나기 전에 사라졌다(패널이 뜨지 않던 원인). 붙잡는 동안
 * 뒤이은 ACL_CONNECTED는 대기하므로, 재연결 여부는 브로드캐스트가 아니라 [VehicleLink]로 직접 묻는다.
 * 백그라운드 브로드캐스트의 수신기 제한(60초)보다 훨씬 짧게 끝낸다.
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
                        if (bluetoothTurningOff(app)) {
                            Log.i(TAG, "disconnect caused by Bluetooth off — not a parking event")
                            app.container.settings.setVehicleLink(connected = false, atMs = now)
                            return@launch
                        }
                        val window = settings.reconnectCheckMs.coerceIn(1_000L, MAX_WINDOW_MS)
                        val candidate = app.container.parking.beginCandidate(registered, now, null, window)
                        if (candidate == null) {
                            Log.i(TAG, "duplicate disconnect merged into existing candidate")
                        } else {
                            Log.i(TAG, "vehicle disconnected — candidate ${candidate.id} checking for ${window}ms")
                            handleDisconnect(app, registered, candidate.id, window)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "vehicle event failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleDisconnect(app: CarApp, address: String, candidateId: String, checkMs: Long) {
        val container = app.container
        // 해제 직후 기압 스냅샷. 재연결 확인과 동시에, 확인 시간 안에 끝나도록 측정한다
        val pressure = scope.async { container.pressure.sample(windowMs = minOf(1_500L, checkMs), timeoutMs = checkMs + 500L) }
        delay(checkMs)
        val reading = pressure.await()
        if (reading != null) {
            container.db.dao().candidate(candidateId)?.let {
                container.db.dao().upsertCandidate(it.copy(pressureHpa = reading.hpa, pressureAt = reading.measuredAtMs))
            }
        }
        if (bluetoothTurningOff(app)) {
            container.parking.cancelCandidate(candidateId)
            return
        }
        // 시동만 껐다 켠 경우: 차량이 다시 연결돼 있으면 주차가 아니다
        if (VehicleLink.isConnected(app, address, LINK_QUERY_MS) == true) {
            Log.i(TAG, "vehicle reconnected within check window — candidate cancelled")
            container.parking.cancelCandidate(candidateId)
            container.settings.setVehicleLink(connected = true, atMs = System.currentTimeMillis())
            return
        }
        if (!container.parking.finishReconnectCheck(candidateId)) {
            Log.i(TAG, "candidate no longer checking — nothing to show")
            return
        }
        val wasBackground = withContext(Dispatchers.Main) {
            !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val launched = AutoLauncher.launchCandidate(app, candidateId, wasBackground)
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
        /** 수신기를 붙잡는 상한. 기압·연결 조회를 더해도 백그라운드 수신기 제한 안에 끝난다 */
        private const val MAX_WINDOW_MS = 10_000L
        /** 프로필 연결 조회 제한. 두 프로필을 동시에 물어 이 시간 안에 끝낸다 */
        private const val LINK_QUERY_MS = 800L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
