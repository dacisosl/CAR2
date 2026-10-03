package app.car.parking.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PairedDevice(val address: String, val name: String?, val likelyVehicle: Boolean)

/** 이미 페어링된 기기 목록. 검색 권한 없이 BLUETOOTH_CONNECT만 사용한다. */
object BondedDevices {
    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isSupported(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter != null

    fun isEnabled(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun list(context: Context): List<PairedDevice> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return emptyList()
        return bonded.map { device ->
            val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
            val minor = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
            PairedDevice(
                address = device.address,
                name = runCatching { device.name }.getOrNull(),
                likelyVehicle = minor == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    minor == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                    major == BluetoothClass.Device.Major.AUDIO_VIDEO && minor != BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES &&
                    minor != BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            )
        }.sortedWith(compareByDescending<PairedDevice> { it.likelyVehicle }.thenBy { it.name ?: it.address })
    }
}
