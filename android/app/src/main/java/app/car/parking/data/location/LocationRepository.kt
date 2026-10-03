package app.car.parking.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtMs: Long,
    val precise: Boolean,
)

enum class UnavailableReason { NoPermission, LocationOff, NoFix }

/** 화면용 실시간 위치 상태. 저장된 주차 위치(ParkingRecord)와 분리한다. */
sealed interface CurrentLocationState {
    data object Locating : CurrentLocationState
    data class Available(val fix: Fix) : CurrentLocationState
    /** 대략적 위치 권한이거나 정확도 범위가 넓다 */
    data class Approximate(val fix: Fix) : CurrentLocationState
    /** 마지막 좌표가 오래되었다. 현재 위치처럼 보여주지 않는다 */
    data class Stale(val fix: Fix) : CurrentLocationState
    data class Unavailable(val reason: UnavailableReason) : CurrentLocationState

    val usableFix: Fix?
        get() = when (this) {
            is Available -> fix
            is Approximate -> fix
            else -> null
        }

    companion object {
        const val STALE_AFTER_MS = 2 * 60 * 1000L
        const val APPROXIMATE_ACCURACY_M = 100f

        fun classify(fix: Fix, nowMs: Long): CurrentLocationState = when {
            nowMs - fix.capturedAtMs > STALE_AFTER_MS -> Stale(fix)
            !fix.precise || (fix.accuracyMeters ?: Float.MAX_VALUE) > APPROXIMATE_ACCURACY_M -> Approximate(fix)
            else -> Available(fix)
        }
    }
}

class LocationRepository(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasFine(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasAny(): Boolean = hasFine() || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 최신 위치 1회. 앱이 화면에 보일 때 사용한다. */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(timeoutMs: Long = 8_000L): Fix? {
        if (!hasAny() || !isLocationEnabled()) return null
        val cts = CancellationTokenSource()
        val priority = if (hasFine()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                client.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { cont.resume(it?.toFix()) }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    }

    /** 화면 활성 동안의 위치 업데이트. 수집을 멈추면 요청이 해제된다. */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 3_000L): Flow<Fix> = callbackFlow {
        if (!hasAny()) {
            close()
            return@callbackFlow
        }
        val priority = if (hasFine()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.toFix() = Fix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        capturedAtMs = time,
        precise = hasFine(),
    )
}
