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
import app.car.parking.platform.update.AppUpdater
import app.car.parking.ui.theme.ElapsedTone
import app.car.parking.domain.parking.ParkingRepository
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
        locationServiceOn = true,
        batteryUnrestricted = true,
        unusedAppExempt = true,
        liveUpdatesAllowed = null,
        hasBarometer = true,
    )
    private val ready = AppSettings(registeredVehicleAddress = "AA:BB")

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
        assertEquals(AutoRecordState.Unsupported, Readiness.state(ready, allGood.copy(bluetoothSupported = false)))
    }
}

class HomeStatusBarDefaultTest {
    private val homeLat = 37.5665
    private val homeLng = 126.9780

    @Test
    fun onNearHomeOffElsewhere() {
        assertTrue(ParkingRepository.isNearHome(homeLat, homeLng, homeLat + 0.0005, homeLng))
        assertEquals(false, ParkingRepository.isNearHome(homeLat, homeLng, homeLat + 0.01, homeLng))
    }

    @Test
    fun offWhenHomeOrLocationUnknown() {
        assertEquals(false, ParkingRepository.isNearHome(null, null, homeLat, homeLng))
        assertEquals(false, ParkingRepository.isNearHome(homeLat, homeLng, null, null))
    }
}

class ElapsedToneTest {
    private val normal = androidx.compose.ui.graphics.Color.Black

    @Test
    fun changesAtTwoAndThreeHours() {
        assertEquals(normal, ElapsedTone.color(ElapsedTone.TWO_HOURS_MS - 1, normal))
        assertEquals(ElapsedTone.Green, ElapsedTone.color(ElapsedTone.TWO_HOURS_MS, normal))
        assertEquals(ElapsedTone.Green, ElapsedTone.color(ElapsedTone.THREE_HOURS_MS - 1, normal))
        assertEquals(ElapsedTone.Burgundy, ElapsedTone.color(ElapsedTone.THREE_HOURS_MS, normal))
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

class UpdateVersionTest {
    @Test
    fun comparesVersions() {
        assertTrue(AppUpdater.isNewer("0.3.1", "0.3.0"))
        assertTrue(AppUpdater.isNewer("0.10.0", "0.9.9"))
        assertTrue(AppUpdater.isNewer("1.0", "0.9.9"))
        assertEquals(false, AppUpdater.isNewer("0.3.1", "0.3.1"))
        assertEquals(false, AppUpdater.isNewer("0.3.0", "0.3.1"))
    }
}

class FloorSignTest {
    private fun sign(label: String) = app.car.parking.platform.statusbar.FloorSign.of(label)
    private val B = app.car.parking.platform.statusbar.FloorSign.BLACK
    private val R = app.car.parking.platform.statusbar.FloorSign.BURGUNDY
    private val G = app.car.parking.platform.statusbar.FloorSign.GREEN
    private val W = app.car.parking.platform.statusbar.FloorSign.WHITE

    @Test
    fun aboveGroundPlateCyclesBlackBurgundyGreen() {
        assertEquals(listOf(B, R, G, B, R, G), listOf("1F", "2F", "3F", "4F", "5F", "6F").map { sign(it).plate })
        listOf("1F", "2F", "3F", "4F").forEach { assertEquals(W, sign(it).text) }
    }

    @Test
    fun basementPlateCyclesBlackBurgundyGreen() {
        assertEquals(listOf(B, R, G, B, R, G), listOf("B1", "B2", "B3", "B4", "B5", "B6").map { sign(it).plate })
        listOf("B1", "B2", "B3", "B4").forEach { assertEquals(W, sign(it).text) }
    }

    @Test
    fun unselectedIsBlackPlate() {
        assertEquals(B, sign("P").plate)
        assertEquals(W, sign("P").text)
    }
}
