package app.car.parking

import app.car.parking.domain.floor.Confidence
import app.car.parking.domain.floor.FloorEstimator
import app.car.parking.domain.floor.FloorRecommendation
import app.car.parking.domain.floor.FloorReference
import app.car.parking.domain.floor.Floors
import app.car.parking.domain.floor.NoRecommendationReason
import app.car.parking.domain.floor.PressureSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorsTest {
    @Test
    fun reelHasNoZeroFloorAndRunsHighToLow() {
        val reel = Floors.reel()
        assertFalse(0 in reel)
        assertEquals(listOf("6F", "5F", "4F", "3F", "2F", "1F", "B1", "B2", "B3", "B4", "B5", "B6"), reel.map { Floors.label(it) })
    }

    @Test
    fun shiftCrossesGroundWithoutZero() {
        assertEquals(-1, Floors.shift(1, -1))
        assertEquals(1, Floors.shift(-1, 1))
        assertEquals(-2, Floors.shift(1, -2))
        assertEquals(2, Floors.shift(-1, 2))
        assertEquals(-3, Floors.shift(-1, -2))
    }

    @Test
    fun labelsRoundTrip() {
        for (level in Floors.reel()) assertEquals(level, Floors.parse(Floors.label(level)))
        assertEquals(null, Floors.label(0))
        assertEquals(null, Floors.parse("0F"))
    }
}

class FloorEstimatorTest {
    private val t0 = 1_000_000_000L
    private val lat = 37.5665
    private val lng = 126.9780

    private fun pressureAt(heightM: Float, base: Float = 1010f): Float {
        // 기준 높이에서 heightM만큼 이동했을 때의 기압(표준 대기 역산)
        val baseAlt = FloorEstimator.altitude(base)
        val target = baseAlt + heightM
        return (1013.25 * Math.pow(1 - target / 44330.0, 5.255)).toFloat()
    }

    private fun ref(level: Int = 1, hpa: Float = 1010f, at: Long = t0) = FloorReference(level, hpa, at, lat, lng)

    @Test
    fun noReferenceMeansNoRecommendation() {
        val result = FloorEstimator.estimate(PressureSample(1010f, t0, lat, lng), null)
        assertEquals(FloorRecommendation.None(NoRecommendationReason.NoReference), result)
    }

    @Test
    fun noSensorMeansManual() {
        val result = FloorEstimator.estimate(PressureSample(1010f, t0, lat, lng), ref(), hasSensor = false)
        assertEquals(FloorRecommendation.None(NoRecommendationReason.NoSensor), result)
    }

    @Test
    fun oldReferenceIsNotReused() {
        val sample = PressureSample(1010f, t0 + FloorEstimator.MAX_REFERENCE_AGE_MS + 1, lat, lng)
        assertEquals(FloorRecommendation.None(NoRecommendationReason.ReferenceTooOld), FloorEstimator.estimate(sample, ref()))
    }

    @Test
    fun differentPlaceIsRejected() {
        val sample = PressureSample(1010f, t0 + 60_000, lat + 0.01, lng)
        assertEquals(FloorRecommendation.None(NoRecommendationReason.DifferentPlace), FloorEstimator.estimate(sample, ref()))
    }

    @Test
    fun unknownLocationIsRejected() {
        val sample = PressureSample(1010f, t0 + 60_000, null, null)
        assertEquals(FloorRecommendation.None(NoRecommendationReason.UnknownPlace), FloorEstimator.estimate(sample, ref()))
    }

    @Test
    fun twoFloorsDownFromGroundIsB2() {
        val sample = PressureSample(pressureAt(-6f), t0 + 5 * 60_000, lat, lng)
        val result = FloorEstimator.estimate(sample, ref(level = 1))
        assertTrue(result is FloorRecommendation.Suggested)
        result as FloorRecommendation.Suggested
        assertEquals(-2, result.level)
        assertEquals(Confidence.High, result.confidence)
    }

    @Test
    fun oneFloorUpFromB1IsGround() {
        val sample = PressureSample(pressureAt(3f), t0 + 60_000, lat, lng)
        val result = FloorEstimator.estimate(sample, ref(level = -1)) as FloorRecommendation.Suggested
        assertEquals(1, result.level)
    }

    @Test
    fun halfwayHeightIsAmbiguous() {
        val sample = PressureSample(pressureAt(-4.5f), t0 + 60_000, lat, lng)
        assertEquals(FloorRecommendation.None(NoRecommendationReason.Ambiguous), FloorEstimator.estimate(sample, ref()))
    }
}
