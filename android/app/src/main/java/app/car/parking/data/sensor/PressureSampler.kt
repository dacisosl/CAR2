package app.car.parking.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class PressureReading(val hpa: Float, val measuredAtMs: Long, val sampleCount: Int)

/** 기압 센서 단발 측정. 센서가 없거나 이벤트가 오지 않으면 null을 돌려준다. */
class PressureSampler(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasBarometer: Boolean get() = sensor != null

    /**
     * [windowMs] 동안 샘플을 모아 중앙값을 돌려준다. 백그라운드에서 센서 이벤트가 제한되는 기기에서는
     * 샘플이 없어 null이 될 수 있다(실기기 검증 항목).
     */
    suspend fun sample(windowMs: Long = 1_500L, timeoutMs: Long = 3_000L): PressureReading? {
        val target = sensor ?: return null
        val manager = sensorManager ?: return null
        val values = mutableListOf<Float>()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        values += event.values[0]
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val finish = Runnable {
                    manager.unregisterListener(listener)
                    if (cont.isActive) {
                        val sorted = values.sorted()
                        cont.resume(
                            sorted.getOrNull(sorted.size / 2)?.let {
                                PressureReading(it, System.currentTimeMillis(), sorted.size)
                            }
                        )
                    }
                }
                manager.registerListener(listener, target, SensorManager.SENSOR_DELAY_UI, handler)
                handler.postDelayed(finish, windowMs)
                cont.invokeOnCancellation {
                    handler.removeCallbacks(finish)
                    manager.unregisterListener(listener)
                }
            }
        }
    }
}
