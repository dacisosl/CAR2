package app.car.parking

import app.car.parking.data.location.CurrentLocationState
import app.car.parking.data.location.Fix
import app.car.parking.data.storage.AppSettings
import app.car.parking.data.storage.AppThemeId
import app.car.parking.data.storage.DrawerSide
import app.car.parking.platform.permissions.AutoRecordState
import app.car.parking.platform.permissions.Readiness
import app.car.parking.platform.permissions.SystemChecks
import app.car.parking.ui.home.elapsedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndSideTest {
    @Test
    fun unknownOrMissingThemeFallsBackToClassic() {
        assertEquals(AppThemeId.Graphite, AppThemeId.from(null))
        assertEquals(AppThemeId.Graphite, AppThemeId.from("navy"))
        assertEquals(AppThemeId.Silver, AppThemeId.from("steel"))
        assertEquals(AppThemeId.Graphite, AppThemeId.from("classic"))
        assertEquals(AppThemeId.Forest, AppThemeId.from("forest"))
        assertEquals(AppThemeId.Espresso, AppThemeId.from("espresso"))
    }

    @Test
    fun drawerSideDefaultsLeft() {
        assertEquals(DrawerSide.Left, DrawerSide.from(null))
        assertEquals(DrawerSide.Right, DrawerSide.from("right"))
        assertEquals(DrawerSide.Left, DrawerSide.Right.opposite())
    }
}

class ReadinessTest {
    private val allGood = SystemChecks(
        bluetoothSupported = true,
        bluetoothEnabled = true,
        bluetoothPermission = true,
        overlayAllowed = true,
        notificationsAllowed = true,
        fineLocation = true,
        anyLocation = true,
        batteryUnrestricted = true,
        hasBarometer = true,
    )
    private val ready = AppSettings(registeredVehicleAddress = "AA:BB", autoLaunchTestPassedAt = 1L)

    @Test
    fun readyOnlyWhenEverythingIsVerified() {
        assertEquals(AutoRecordState.Ready, Readiness.state(ready, allGood))
    }

    @Test
    fun notReadyStates() {
        assertEquals(AutoRecordState.VehicleMissing, Readiness.state(ready.copy(registeredVehicleAddress = null), allGood))
        assertEquals(AutoRecordState.PermissionMissing, Readiness.state(ready, allGood.copy(overlayAllowed = false)))
        assertEquals(AutoRecordState.PermissionMissing, Readiness.state(ready, allGood.copy(bluetoothPermission = false)))
        assertEquals(AutoRecordState.MonitoringStopped, Readiness.state(ready.copy(autoRecordRequested = false), allGood))
        assertEquals(AutoRecordState.MonitoringStopped, Readiness.state(ready, allGood.copy(bluetoothEnabled = false)))
        assertEquals(AutoRecordState.NeedsDeviceTest, Readiness.state(ready.copy(autoLaunchTestPassedAt = 0L), allGood))
        assertEquals(AutoRecordState.Unsupported, Readiness.state(ready, allGood.copy(bluetoothSupported = false)))
    }
}

class LocationStateTest {
    private val now = 10_000_000L
    private fun fix(age: Long, acc: Float? = 10f, precise: Boolean = true) = Fix(37.0, 127.0, acc, now - age, precise)

    @Test
    fun classifiesFreshApproximateAndStale() {
        assertTrue(CurrentLocationState.classify(fix(1_000), now) is CurrentLocationState.Available)
        assertTrue(CurrentLocationState.classify(fix(1_000, precise = false), now) is CurrentLocationState.Approximate)
        assertTrue(CurrentLocationState.classify(fix(1_000, acc = 500f), now) is CurrentLocationState.Approximate)
        assertTrue(CurrentLocationState.classify(fix(CurrentLocationState.STALE_AFTER_MS + 1), now) is CurrentLocationState.Stale)
    }

    @Test
    fun staleFixIsNotUsableForRecenter() {
        assertEquals(null, CurrentLocationState.classify(fix(CurrentLocationState.STALE_AFTER_MS + 1), now).usableFix)
    }
}

class ElapsedTextTest {
    @Test
    fun formatsElapsed() {
        assertEquals("방금", elapsedText(10_000))
        assertEquals("13분", elapsedText(13 * 60_000L))
        assertEquals("2시간 13분", elapsedText((2 * 60 + 13) * 60_000L))
        assertEquals("1일 3시간", elapsedText((27 * 60 + 5) * 60_000L))
    }
}
