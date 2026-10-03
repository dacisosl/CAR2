package app.car.parking.platform.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.car.parking.data.bluetooth.BondedDevices
import app.car.parking.data.storage.AppSettings
import app.car.parking.platform.autolaunch.AutoLauncher

/** 자동 기록 준비 상태. 영속 설정의 요청 값과 현재 OS 상태를 합쳐 매번 다시 계산한다. */
enum class AutoRecordState {
    Ready,
    VehicleMissing,
    PermissionMissing,
    MonitoringStopped,
    Unsupported,
    NeedsDeviceTest,
}

data class SystemChecks(
    val bluetoothSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val bluetoothPermission: Boolean,
    val overlayAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val fineLocation: Boolean,
    val anyLocation: Boolean,
    val batteryUnrestricted: Boolean,
    val hasBarometer: Boolean,
) {
    companion object {
        fun read(context: Context, hasBarometer: Boolean): SystemChecks {
            val power = context.getSystemService(PowerManager::class.java)
            fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            return SystemChecks(
                bluetoothSupported = BondedDevices.isSupported(context),
                bluetoothEnabled = BondedDevices.isEnabled(context),
                bluetoothPermission = BondedDevices.hasConnectPermission(context),
                overlayAllowed = AutoLauncher.canDrawOverlays(context),
                notificationsAllowed = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                anyLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION),
                batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
                hasBarometer = hasBarometer,
            )
        }
    }
}

object Readiness {
    fun state(settings: AppSettings, checks: SystemChecks): AutoRecordState = when {
        !checks.bluetoothSupported -> AutoRecordState.Unsupported
        settings.registeredVehicleAddress == null -> AutoRecordState.VehicleMissing
        !checks.bluetoothPermission || !checks.overlayAllowed -> AutoRecordState.PermissionMissing
        !settings.autoRecordRequested || !checks.bluetoothEnabled -> AutoRecordState.MonitoringStopped
        settings.autoLaunchTestPassedAt == 0L -> AutoRecordState.NeedsDeviceTest
        else -> AutoRecordState.Ready
    }

    /** Bluetooth 아이콘 접근성 설명. 실제 차량 연결 여부가 아니라 자동 기록 준비 상태다. */
    fun description(state: AutoRecordState): String = when (state) {
        AutoRecordState.Ready -> "자동 기록 준비됨"
        AutoRecordState.VehicleMissing -> "자동 기록 꺼짐, 차량 미등록"
        AutoRecordState.PermissionMissing -> "자동 기록 꺼짐, 권한 필요"
        AutoRecordState.MonitoringStopped -> "자동 기록 꺼짐"
        AutoRecordState.Unsupported -> "자동 기록 미지원 기기"
        AutoRecordState.NeedsDeviceTest -> "자동 기록 준비 중, 자동 표시 테스트 필요"
    }
}
