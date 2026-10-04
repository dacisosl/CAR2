package app.car.parking.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 등록 차량이 지금 실제로 연결돼 있는지 묻는다. 브로드캐스트 순서나 놓친 이벤트와 무관하게
 * 오디오(A2DP)·핸즈프리(HEADSET) 프로필에서 그 주소가 연결 목록에 있으면 연결로 본다.
 *
 * null: 알 수 없음(권한 없음, 어댑터 없음, 응답 없음). 호출한 쪽이 보수적으로 처리한다.
 */
object VehicleLink {
    private val PROFILES = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)

    suspend fun isConnected(context: Context, address: String, timeoutMs: Long = 2_000L): Boolean? {
        if (!BondedDevices.hasConnectPermission(context)) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (adapter.state != BluetoothAdapter.STATE_ON) return false
        var known = false
        for (profile in PROFILES) {
            when (connectedIn(context, adapter, profile, address, timeoutMs)) {
                true -> return true
                false -> known = true
                null -> {}
            }
        }
        return if (known) false else null
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectedIn(
        context: Context,
        adapter: BluetoothAdapter,
        profile: Int,
        address: String,
        timeoutMs: Long,
    ): Boolean? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val ok = adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        val connected = runCatching {
                            proxy.connectedDevices.any { it.address.equals(address, ignoreCase = true) }
                        }.getOrNull()
                        runCatching { adapter.closeProfileProxy(p, proxy) }
                        if (cont.isActive) cont.resume(connected)
                    }

                    override fun onServiceDisconnected(p: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
                profile,
            )
            if (!ok && cont.isActive) cont.resume(null)
        }
    }
}
