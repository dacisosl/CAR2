package app.car.parking.domain.parking

import app.car.parking.data.location.Fix
import app.car.parking.data.sensor.PressureReading
import app.car.parking.data.storage.CandidateEntity
import app.car.parking.data.storage.CandidateStatus
import app.car.parking.data.storage.DetectionSource
import app.car.parking.data.storage.FloorReferenceEntity
import app.car.parking.data.storage.LocationSource
import app.car.parking.data.storage.ParkingDao
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.data.storage.SettingsStore
import app.car.parking.domain.floor.FloorEstimator
import app.car.parking.domain.floor.FloorRecommendation
import app.car.parking.domain.floor.FloorReference
import app.car.parking.domain.floor.PressureSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** 패널을 연 이유. 저장 시 어떤 기록을 만들거나 고칠지 결정한다. */
sealed interface DrawerTarget {
    data class Candidate(val candidateId: String) : DrawerTarget
    data class Edit(val recordId: String) : DrawerTarget
    data object Manual : DrawerTarget
}

class ParkingRepository(
    private val dao: ParkingDao,
    private val settings: SettingsStore,
) {
    private val candidateLock = Mutex()

    val latestRecord: Flow<ParkingRecordEntity?> = dao.observeLatestRecord()
    val pendingCandidate: Flow<CandidateEntity?> = dao.observeCandidate(CandidateStatus.READY)

    suspend fun latestRecordNow(): ParkingRecordEntity? = dao.latestRecord()
    suspend fun candidate(id: String): CandidateEntity? = dao.candidate(id)

    /** 등록 차량 연결. 확인 중인 후보와 확인하지 않은 후보를 취소한다. 확정 기록은 지우지 않는다. */
    suspend fun onVehicleConnected(vehicleId: String, atMs: Long) = candidateLock.withLock {
        settings.setLastConnectedAt(atMs)
        dao.moveCandidates(vehicleId, CandidateStatus.CHECKING, CandidateStatus.CANCELLED)
        dao.moveCandidates(vehicleId, CandidateStatus.READY, CandidateStatus.CANCELLED)
    }

    /**
     * 연결 해제 직후 후보를 '확인 중'으로 만든다. 같은 연결 세션의 중복 해제는 null.
     * 기압은 해제 시점 스냅샷만 저장한다.
     */
    suspend fun beginCandidate(vehicleId: String, detectedAtMs: Long, pressure: PressureReading?): CandidateEntity? =
        candidateLock.withLock {
            val connectedAt = settings.current().lastConnectedAt
            // 연결 이벤트가 먼저 처리된 경우: 이 해제는 이미 지난 연결 세션의 것이다
            if (connectedAt > 0L && detectedAtMs < connectedAt) return@withLock null
            val sessionKey = if (connectedAt > 0L) "$vehicleId@$connectedAt" else "$vehicleId@d$detectedAtMs"
            if (dao.candidateBySession(sessionKey) != null) return@withLock null
            val active = dao.activeCandidate(vehicleId)
            if (active != null && (connectedAt == 0L || active.detectedAt >= connectedAt)) return@withLock null
            val candidate = CandidateEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                sessionKey = sessionKey,
                detectedAt = detectedAtMs,
                status = CandidateStatus.CHECKING,
                pressureHpa = pressure?.hpa,
                pressureAt = pressure?.measuredAtMs,
                latitude = null,
                longitude = null,
                locationAccuracyMeters = null,
                locationCapturedAt = null,
                locationSource = LocationSource.UNAVAILABLE,
            )
            if (dao.insertCandidate(candidate) == -1L) null else candidate
        }

    /** 재연결 확인 시간이 끝났을 때. 아직 확인 중이면 표시 대상으로 바꾸고 true. */
    suspend fun finishReconnectCheck(candidateId: String): Boolean = candidateLock.withLock {
        val current = dao.candidate(candidateId) ?: return@withLock false
        if (current.status != CandidateStatus.CHECKING) return@withLock false
        dao.setCandidateStatus(candidateId, CandidateStatus.READY)
        true
    }

    suspend fun cancelCandidate(candidateId: String) {
        dao.setCandidateStatus(candidateId, CandidateStatus.CANCELLED)
    }

    /** 앱이 표시된 직후의 현재 위치를 후보에 붙인다. 이미 좌표가 있으면 바꾸지 않는다. */
    suspend fun attachLocation(candidateId: String, fix: Fix) {
        val current = dao.candidate(candidateId) ?: return
        if (current.latitude != null || current.status != CandidateStatus.READY) return
        dao.upsertCandidate(
            current.copy(
                latitude = fix.latitude,
                longitude = fix.longitude,
                locationAccuracyMeters = fix.accuracyMeters,
                locationCapturedAt = fix.capturedAtMs,
                locationSource = LocationSource.AFTER_DISCONNECT,
            )
        )
    }

    suspend fun recommendationFor(candidate: CandidateEntity, hasBarometer: Boolean): FloorRecommendation {
        val sample = candidate.pressureHpa?.let {
            PressureSample(it, candidate.pressureAt ?: candidate.detectedAt, candidate.latitude, candidate.longitude)
        }
        val reference = dao.latestReference()?.let {
            FloorReference(it.floorLevel, it.pressureHpa, it.measuredAt, it.latitude, it.longitude, it.floorHeightM)
        }
        return FloorEstimator.estimate(sample, reference, hasBarometer)
    }

    /**
     * 사용자가 저장을 누른 경우에만 확정 기록을 쓴다.
     * 주차 시작 시각은 원래 감지 시각을 유지한다.
     */
    suspend fun confirm(
        target: DrawerTarget,
        floorLevel: Int,
        photoPath: String?,
        manualFix: Fix?,
        vehicleId: String?,
        statusBar: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): ParkingRecordEntity? {
        val record = when (target) {
            is DrawerTarget.Candidate -> {
                // 패널이 열린 사이 차량이 다시 연결돼 취소된 후보는 기록하지 않는다
                val candidate = dao.candidate(target.candidateId)?.takeIf { it.status == CandidateStatus.READY } ?: return null
                dao.setCandidateStatus(candidate.id, CandidateStatus.CONFIRMED)
                if (candidate.pressureHpa != null) {
                    dao.insertReference(
                        FloorReferenceEntity(
                            floorLevel = floorLevel,
                            pressureHpa = candidate.pressureHpa,
                            measuredAt = candidate.pressureAt ?: candidate.detectedAt,
                            latitude = candidate.latitude,
                            longitude = candidate.longitude,
                            floorHeightM = FloorEstimator.DEFAULT_FLOOR_HEIGHT_M,
                        )
                    )
                }
                ParkingRecordEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = candidate.vehicleId,
                    detectedAt = candidate.detectedAt,
                    confirmedAt = nowMs,
                    floorLevel = floorLevel,
                    placeName = null,
                    zoneMemo = null,
                    photoPath = photoPath,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    locationAccuracyMeters = candidate.locationAccuracyMeters,
                    locationCapturedAt = candidate.locationCapturedAt,
                    locationSource = candidate.locationSource,
                    detectionSource = DetectionSource.BLUETOOTH,
                    statusBarShown = statusBar,
                )
            }
            is DrawerTarget.Edit -> {
                val existing = dao.latestRecord()?.takeIf { it.id == target.recordId } ?: return null
                existing.copy(
                    floorLevel = floorLevel,
                    photoPath = photoPath ?: existing.photoPath,
                    confirmedAt = nowMs,
                    statusBarShown = statusBar,
                )
            }
            DrawerTarget.Manual -> ParkingRecordEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                detectedAt = nowMs,
                confirmedAt = nowMs,
                floorLevel = floorLevel,
                placeName = null,
                zoneMemo = null,
                photoPath = photoPath,
                latitude = manualFix?.latitude,
                longitude = manualFix?.longitude,
                locationAccuracyMeters = manualFix?.accuracyMeters,
                locationCapturedAt = manualFix?.capturedAtMs,
                locationSource = if (manualFix != null) LocationSource.MANUAL else LocationSource.UNAVAILABLE,
                detectionSource = DetectionSource.MANUAL,
                statusBarShown = statusBar,
            )
        }
        dao.upsertRecord(record)
        return record
    }

    data class PhotoResult(val attached: Boolean, val previousPath: String?)

    /** 홈 차량 카드에서 찍은 사진을 현재 기록에 붙인다 */
    suspend fun setPhoto(recordId: String, path: String): PhotoResult {
        val existing = dao.latestRecord()?.takeIf { it.id == recordId } ?: return PhotoResult(false, null)
        dao.upsertRecord(existing.copy(photoPath = path))
        return PhotoResult(true, existing.photoPath)
    }

    /** 홈의 위치 저장 아이콘. 현재 위치를 이 기록의 주차 위치로 저장한다 */
    suspend fun setLocation(recordId: String, fix: Fix): ParkingRecordEntity? {
        val existing = dao.latestRecord()?.takeIf { it.id == recordId } ?: return null
        val updated = existing.copy(
            latitude = fix.latitude,
            longitude = fix.longitude,
            locationAccuracyMeters = fix.accuracyMeters,
            locationCapturedAt = fix.capturedAtMs,
            locationSource = LocationSource.SAVED,
        )
        dao.upsertRecord(updated)
        return updated
    }

    companion object {
        const val HOME_RADIUS_M = 200.0

        /** 집 근처면 상태바 기본 켜짐, 그 외(집 미등록·위치 모름 포함) 기본 꺼짐 */
        fun isNearHome(homeLat: Double?, homeLng: Double?, lat: Double?, lng: Double?): Boolean {
            if (homeLat == null || homeLng == null || lat == null || lng == null) return false
            return FloorEstimator.distanceMeters(homeLat, homeLng, lat, lng) <= HOME_RADIUS_M
        }
    }
}
