package app.car.parking.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/**
 * 권한 요청과 시스템 설정 이동. 결과는 돌아온 뒤 onResume에서 실제 상태로 다시 확인한다.
 * 권한을 허용했다고 임의로 표시하지 않는다.
 */
class SystemActions(
    private val context: Context,
    private val request: (Array<String>) -> Unit,
) {
    fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) requestOrOpen(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
    }

    fun requestLocation() = requestOrOpen(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestOrOpen(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    fun openOverlaySettings() = start(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
    )

    /** 배터리 최적화 예외 목록. 앱에서 직접 예외를 요청하지 않고 사용자가 선택하게 한다 */
    fun openBatterySettings() = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openBluetoothSettings() = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openLocationSettings() = start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun openAppDetails() = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    private val activity: Activity?
        get() {
            var c = context
            while (c is ContextWrapper) {
                if (c is Activity) return c
                c = c.baseContext
            }
            return null
        }

    private fun requestOrOpen(permissions: Array<String>) {
        val prefs = context.getSharedPreferences("permission_asks", Context.MODE_PRIVATE)
        val key = permissions.first()
        val askedBefore = prefs.getBoolean(key, false)
        val rationale = activity?.let { a -> permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(a, it) } } ?: false
        // 이전에 거부해 시스템이 더 이상 묻지 않으면 앱 설정으로 안내한다
        if (askedBefore && !rationale) {
            openAppDetails()
        } else {
            prefs.edit().putBoolean(key, true).apply()
            request(permissions)
        }
    }

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }
        }
    }
}

@Composable
fun rememberSystemActions(onResult: () -> Unit): SystemActions {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return remember(context) { SystemActions(context) { launcher.launch(it) } }
}
