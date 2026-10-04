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
import app.car.parking.CarApp
import app.car.parking.data.storage.CandidateStatus
import app.car.parking.platform.autolaunch.AutoLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 등록 차량의 ACL 연결/해제 수신. ACL_CONNECTED/DISCONNECTED는 매니페스트 수신기 허용 예외라
 * 앱 프로세스가 없어도 전달된다(시스템 ‘강제 중지’ 상태는 제외 — 실기기 검증 항목).
 *
 * 연결 → ‘이동 중’ 상태 저장, 확인 중·막 띄운 후보 취소(열려 있던 패널은 앱이 닫는다).
 * 해제 → 후보 생성 → **기다리지 않고 바로 앱 자동 표시** → 뒤에서 기압 스냅샷·재연결 확인.
 *
 * 체감 속도를 위해 재연결 확인 전에 패널부터 띄운다. 앱이 앞으로 오면 프로세스가 유지되므로
 * 확인은 앱 범위 코루틴에서 이어서 한다. 시동만 껐다 켠 경우(확인 시간 안에 다시 연결) 후보를 취소하고,
 * 화면의 패널은 [app.car.parking.ui.AppViewModel]이 후보 취소를 보고 저장 없이 닫는다.
 */
class VehicleEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val device: BluetoothDevice = intentDevice(intent) ?: return
        handle(context.applicationContext as CarApp, action, device.address, goAsync())
    }

    /**
     * 수신기 본문. 디버그 빌드의 시뮬레이션 수신기(src/debug, adb 전용)도 실제 차량 없이 같은 경로를 탄다.
     * 그때는 시스템 브로드캐스트가 아니므로 [pending]이 null이다.
     */
    internal fun handle(app: CarApp, action: String, address: String, pending: PendingResult?) {
        val now = System.currentTimeMillis()
        scope.launch {
            try {
                val settings = app.container.settings.current()
                val registered = settings.registeredVehicleAddress
                // 이어폰·시계 등 등록하지 않은 기기는 무시한다
                if (registered == null || !registered.equals(address, ignoreCase = true)) return@launch
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
                        // 연결 상태 저장(디스크 동기화)은 후보 생성과 나란히. 이벤트 시각 비교라 늦게 끝나도 재연결 상태를 덮지 않는다.
                        // 별도 범위(SupervisorJob)의 async라 저장이 실패해도 이 작업을 취소하지 않고, 아래 await에서 잡아 기록한다
                        val linkWrite = scope.async { app.container.settings.setVehicleLink(connected = false, atMs = now) }
                        // 표시 대상(READY)으로 바로 만든다: 확인 단계 없이 쓰기 한 번 뒤 곧바로 화면 실행
                        val candidate = app.container.parking.beginCandidate(registered, now, null, window, ready = true)
                        if (candidate == null) {
                            Log.i(TAG, "duplicate disconnect merged into existing candidate")
                        } else {
                            val launched = AutoLauncher.launchCandidate(app, candidate.id)
                            Log.i(TAG, "vehicle disconnected — candidate ${candidate.id} shown immediately, launch=$launched")
                            // 기압은 해제 시점에 재야 하므로 화면 확인을 기다리지 않고 바로 시작한다
                            val verify = app.container.appScope.launch { verifyAfterLaunch(app, registered, candidate.id, window) }
                            // 화면이 실제로 열렸는지 잠깐 본다. 열리지 않았고(실행이 막힘 등) 후보가 아직 기록 대상일 때만 보조 알림
                            val shown = AutoLauncher.awaitShown(candidate.id, if (launched) SHOW_CONFIRM_MS else 0L)
                            if (!shown && app.container.parking.candidate(candidate.id)?.status == CandidateStatus.READY) {
                                Log.i(TAG, "panel not shown — fallback notification posted")
                                AutoLauncher.postFallback(app, candidate.id)
                            }
                            // 화면을 띄우지 못했으면 프로세스를 붙잡아 줄 화면이 없다: 기압·재연결 확인을 수신기 시간 안에 끝낸다
                            if (!launched) verify.join()
                        }
                        linkWrite.await()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "vehicle event failed", t)
            } finally {
                pending?.finish()
            }
        }
    }

    /** 패널을 띄운 뒤: 해제 시점 기압을 붙이고, 확인 시간이 지나 차량이 다시 연결돼 있으면 후보를 취소한다 */
    private suspend fun verifyAfterLaunch(app: CarApp, address: String, candidateId: String, checkMs: Long) {
        val container = app.container
        val pressure = scope.async { container.pressure.sample(windowMs = minOf(1_500L, checkMs), timeoutMs = checkMs + 500L) }
        // 측정이 끝나는 대로 붙인다(확인 시간을 기다리지 않는다). 패널의 층수 추천이 바로 다시 계산되고,
        // 그 사이 이미 저장했으면 저장한 층으로 기준점을 만든다
        val pressureWrite = scope.launch {
            val reading = pressure.await()
            if (reading == null) {
                Log.i(TAG, "no pressure sample (no barometer or timeout)")
            } else {
                val result = container.parking.attachPressure(candidateId, reading)
                Log.i(TAG, "pressure ${reading.hpa} hPa (${reading.sampleCount} samples) → $result")
            }
        }
        delay(checkMs)
        pressureWrite.join()
        if (bluetoothTurningOff(app)) return
        // 시동만 껐다 켠 경우: 차량이 다시 연결돼 있으면 주차가 아니다(연결 이벤트를 놓쳤을 때의 보정).
        // 조회 시작 시각으로 기록해, 조회하는 동안 다시 끊긴 이벤트가 이 결과보다 나중으로 남게 한다
        val queriedAt = System.currentTimeMillis()
        if (VehicleLink.isConnected(app, address, LINK_QUERY_MS) == true) {
            Log.i(TAG, "vehicle reconnected within check window — candidate cancelled")
            container.parking.cancelCandidate(candidateId)
            AutoLauncher.cancelFallback(app)
            container.settings.setVehicleLink(connected = true, atMs = queriedAt)
        }
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
        /** 실행을 요청한 뒤 화면이 후보를 열 때까지 기다리는 시간. 넘으면 보조 알림을 올린다 */
        private const val SHOW_CONFIRM_MS = 2_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
