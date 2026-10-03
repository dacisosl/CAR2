package app.car.parking.platform.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.car.parking.data.bluetooth.BondedDevices
import app.car.parking.data.location.LocationRepository
import app.car.parking.data.storage.AppSettings
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.platform.statusbar.StatusBarNotifier

/** 자동 기록 준비 상태. 영속 설정의 요청 값과 현재 OS 상태를 합쳐 매번 다시 계산한다. */
enum class AutoRecordState {
    Ready,
    VehicleMissing,
    PermissionMissing,
    MonitoringStopped,
    Unsupported,
}

data class SystemChecks(
    val bluetoothSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val bluetoothPermission: Boolean,
    val overlayAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val fineLocation: Boolean,
    val anyLocation: Boolean,
    val locationServiceOn: Boolean,
    val batteryUnrestricted: Boolean,
    /** 오래 쓰지 않으면 권한을 자동 회수하는 기능에서 제외됐는지. Android 10 이하는 해당 없음(true) */
    val unusedAppExempt: Boolean,
    /** Android 16 실시간 업데이트. null이면 해당 없음 */
    val liveUpdatesAllowed: Boolean?,
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
                locationServiceOn = LocationRepository(context).isLocationEnabled(),
                batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
                unusedAppExempt = Build.VERSION.SDK_INT < 30 ||
                    runCatching { context.packageManager.isAutoRevokeWhitelisted }.getOrDefault(true),
                liveUpdatesAllowed = when (StatusBarNotifier.liveUpdatesState(context)) {
                    StatusBarNotifier.LiveUpdatesState.Unsupported -> null
                    StatusBarNotifier.LiveUpdatesState.Ready -> true
                    StatusBarNotifier.LiveUpdatesState.Disabled -> false
                },
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
        else -> AutoRecordState.Ready
    }

    /** Bluetooth 아이콘 접근성 설명. 실제 차량 연결 여부가 아니라 자동 기록 준비 상태다. */
    fun description(state: AutoRecordState): String = when (state) {
        AutoRecordState.Ready -> "자동 기록 준비됨"
        AutoRecordState.VehicleMissing -> "자동 기록 꺼짐, 차량 미등록"
        AutoRecordState.PermissionMissing -> "자동 기록 꺼짐, 권한 필요"
        AutoRecordState.MonitoringStopped -> "자동 기록 꺼짐, Bluetooth 꺼짐"
        AutoRecordState.Unsupported -> "자동 기록 미지원 기기"
    }
}
