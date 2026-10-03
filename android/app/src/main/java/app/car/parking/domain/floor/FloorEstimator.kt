package app.car.parking.domain.floor

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 같은 방문에서 사용자가 확정한 층과 그 시점의 기압. 절대 기압 기준이 아니라 상대 비교용이다. */
data class FloorReference(
    val level: Int,
    val pressureHpa: Float,
    val measuredAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val floorHeightM: Float = FloorEstimator.DEFAULT_FLOOR_HEIGHT_M,
)

data class PressureSample(
    val pressureHpa: Float,
    val measuredAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
)

enum class Confidence { High, Medium, Low, Unavailable }

enum class NoRecommendationReason { NoSensor, NoSample, NoReference, ReferenceTooOld, UnknownPlace, DifferentPlace, Ambiguous }

sealed interface FloorRecommendation {
    data class Suggested(val level: Int, val confidence: Confidence, val heightDiffM: Float) : FloorRecommendation
    data class None(val reason: NoRecommendationReason) : FloorRecommendation
}

/**
 * 기압 기반 층수 추천. 현재 방문의 최근 기준점과 주차 시점의 상대 높이 차이만 사용한다.
 * 근거가 부족하면 추천을 생략하고 수동 선택으로 둔다. 정확도 퍼센트는 만들지 않는다.
 */
object FloorEstimator {
    const val DEFAULT_FLOOR_HEIGHT_M = 3.0f
    const val MAX_REFERENCE_AGE_MS = 30 * 60 * 1000L
    const val MAX_SAME_PLACE_DISTANCE_M = 300.0
    private const val STANDARD_PRESSURE_HPA = 1013.25f

    fun estimate(sample: PressureSample?, reference: FloorReference?, hasSensor: Boolean = true): FloorRecommendation {
        if (!hasSensor) return FloorRecommendation.None(NoRecommendationReason.NoSensor)
        if (sample == null) return FloorRecommendation.None(NoRecommendationReason.NoSample)
        if (reference == null) return FloorRecommendation.None(NoRecommendationReason.NoReference)
        val gap = sample.measuredAtMs - reference.measuredAtMs
        if (gap < 0 || gap > MAX_REFERENCE_AGE_MS) return FloorRecommendation.None(NoRecommendationReason.ReferenceTooOld)
        if (sample.latitude == null || sample.longitude == null || reference.latitude == null || reference.longitude == null) {
            return FloorRecommendation.None(NoRecommendationReason.UnknownPlace)
        }
        val distance = distanceMeters(sample.latitude, sample.longitude, reference.latitude, reference.longitude)
        if (distance > MAX_SAME_PLACE_DISTANCE_M) return FloorRecommendation.None(NoRecommendationReason.DifferentPlace)

        val diff = altitude(sample.pressureHpa) - altitude(reference.pressureHpa)
        val floors = diff / reference.floorHeightM
        val rounded = floors.roundToInt()
        val residual = abs(floors - rounded)
        val confidence = when {
            residual < 0.25f && gap <= 10 * 60 * 1000L -> Confidence.High
            residual < 0.35f -> Confidence.Medium
            else -> Confidence.Low
        }
        if (confidence == Confidence.Low) return FloorRecommendation.None(NoRecommendationReason.Ambiguous)
        return FloorRecommendation.Suggested(Floors.shift(reference.level, rounded), confidence, diff)
    }

    /** SensorManager.getAltitude와 같은 국제 표준 대기식. 두 값의 차이만 의미가 있다. */
    fun altitude(pressureHpa: Float): Float =
        44330f * (1f - (pressureHpa / STANDARD_PRESSURE_HPA).toDouble().pow(1.0 / 5.255).toFloat())

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
