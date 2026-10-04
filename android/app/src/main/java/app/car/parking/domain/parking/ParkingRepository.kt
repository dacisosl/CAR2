package app.car.parking.domain.parking

import app.car.parking.data.location.Fix
import app.car.parking.data.sensor.PressureReading
import app.car.parking.data.storage.CandidateEntity
import app.car.parking.data.storage.CandidateStatus
import app.car.parking.data.storage.DetectionSource
import app.car.parking.data.storage.AppSettings
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

    /**
     * 등록 차량 연결(이동 시작). 확인 중인 후보는 ‘잠깐 끊김’이므로 취소한다.
     * 이미 표시 대상이 된 후보는 사용자가 아직 층을 적지 않은 주차이므로 남겨 둔다
     * (다음 해제 때 새 후보로 바뀐다). 확정 기록은 지우지 않는다.
     */
    suspend fun onVehicleConnected(vehicleId: String, atMs: Long) = candidateLock.withLock {
        settings.setLastConnectedAt(atMs)
        settings.setVehicleLink(connected = true, atMs = atMs)
        dao.moveCandidates(vehicleId, CandidateStatus.CHECKING, CandidateStatus.CANCELLED)
        // 하차 즉시 패널을 띄우므로, 확인 시간 안에 다시 연결되면 이미 표시 중인 후보도 취소한다
        val window = settings.current().reconnectCheckMs.coerceAtLeast(AppSettings.DEFAULT_RECONNECT_MS)
        dao.activeCandidate(vehicleId)?.let { active ->
            if (active.status == CandidateStatus.READY && atMs - active.detectedAt in 0..window + RECONNECT_GRACE_MS) {
                dao.setCandidateStatus(active.id, CandidateStatus.CANCELLED)
            }
        }
    }

    /**
     * 연결 해제 직후 후보를 '확인 중'으로 만든다. 기압은 해제 시점 스냅샷만 저장한다.
     *
     * 중복 판정은 시간으로만 한다: 재연결 확인 시간([dedupWindowMs]) 안에 이미 활성 후보가 있으면
     * 같은 하차의 두 번째 해제(BR/EDR·LE 이중 전송 등)로 보고 null. 그보다 오래된 활성 후보는
     * 지난 주차의 것이므로 취소하고 새 후보를 만든다. 연결 이벤트를 놓쳐도 해제는 항상 기록된다.
     */
    suspend fun beginCandidate(
        vehicleId: String,
        detectedAtMs: Long,
        pressure: PressureReading?,
        dedupWindowMs: Long = AppSettings.DEFAULT_RECONNECT_MS,
        ready: Boolean = false,
    ): CandidateEntity? =
        candidateLock.withLock {
            // 연결 상태 저장(DataStore 디스크 쓰기)은 호출한 쪽이 후보 생성과 나란히 한다
            val active = dao.activeCandidate(vehicleId)
            if (active != null) {
                if (detectedAtMs - active.detectedAt in 0..dedupWindowMs) return@withLock null
                dao.setCandidateStatus(active.id, CandidateStatus.CANCELLED)
            }
            val candidate = CandidateEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                sessionKey = "$vehicleId@$detectedAtMs",
                detectedAt = detectedAtMs,
                status = if (ready) CandidateStatus.READY else CandidateStatus.CHECKING,
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

    /** 표시 대상 후보 중 [maxAgeMs]보다 오래된 것은 지난 주차로 보고 정리한다 */
    suspend fun expireStaleCandidates(vehicleId: String, nowMs: Long, maxAgeMs: Long) = candidateLock.withLock {
        val active = dao.activeCandidate(vehicleId) ?: return@withLock
        if (active.status == CandidateStatus.READY && nowMs - active.detectedAt > maxAgeMs) {
            dao.setCandidateStatus(active.id, CandidateStatus.CANCELLED)
        }
    }

    /** 앱이 표시된 직후의 현재 위치를 후보에 붙인다. 이미 좌표가 있으면 바꾸지 않는다. */
    suspend fun attachLocation(candidateId: String, fix: Fix): Boolean = candidateLock.withLock {
        val current = dao.candidate(candidateId) ?: return@withLock false
        if (current.latitude != null) return@withLock false
        if (current.status != CandidateStatus.READY && current.status != CandidateStatus.CONFIRMED) return@withLock false
        dao.setCandidateLocation(
            candidateId, fix.latitude, fix.longitude, fix.accuracyMeters, fix.capturedAtMs, LocationSource.AFTER_DISCONNECT,
        )
        // 위치가 오기 전에 이미 저장했으면(후보 CONFIRMED) 그 하차로 만든 기록에도 같은 좌표를 채운다
        if (current.status == CandidateStatus.CONFIRMED) {
            dao.fillRecordLocation(
                vehicleId = current.vehicleId,
                detectedAt = current.detectedAt,
                detection = DetectionSource.BLUETOOTH,
                lat = fix.latitude,
                lng = fix.longitude,
                accuracy = fix.accuracyMeters,
                capturedAt = fix.capturedAtMs,
                source = LocationSource.AFTER_DISCONNECT,
            )
            if (current.pressureHpa != null) {
                dao.fillReferenceLocation(current.pressureAt ?: current.detectedAt, fix.latitude, fix.longitude)
            }
        }
        true
    }

    /**
     * 하차 시점 기압을 후보에 붙인다. 패널은 바로 뜨고 기압은 그 뒤에 도착하므로,
     * 그 사이 이미 저장했으면(후보 CONFIRMED) 저장한 층으로 층 기준점을 만든다(저장 때는 기압이 없어 못 만들었다).
     */
    suspend fun attachPressure(candidateId: String, reading: PressureReading): PressureAttach = candidateLock.withLock {
        val current = dao.candidate(candidateId) ?: return@withLock PressureAttach.SKIPPED
        if (current.pressureHpa != null) return@withLock PressureAttach.SKIPPED
        dao.setCandidatePressure(candidateId, reading.hpa, reading.measuredAtMs)
        if (current.status != CandidateStatus.CONFIRMED) return@withLock PressureAttach.ATTACHED
        val record = dao.recordFor(current.vehicleId, current.detectedAt, DetectionSource.BLUETOOTH)
            ?: return@withLock PressureAttach.ATTACHED
        val floor = record.floorLevel ?: return@withLock PressureAttach.ATTACHED
        dao.insertReference(
            FloorReferenceEntity(
                floorLevel = floor,
                pressureHpa = reading.hpa,
                measuredAt = reading.measuredAtMs,
                latitude = current.latitude ?: record.latitude,
                longitude = current.longitude ?: record.longitude,
                floorHeightM = FloorEstimator.DEFAULT_FLOOR_HEIGHT_M,
            )
        )
        PressureAttach.REFERENCE_CREATED
    }

    enum class PressureAttach { SKIPPED, ATTACHED, REFERENCE_CREATED }

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
    ): ParkingRecordEntity? = candidateLock.withLock {
        // attachLocation과 같은 잠금: 저장과 늦게 도착한 하차 위치가 서로를 놓치지 않게 한다
        val record = when (target) {
            is DrawerTarget.Candidate -> {
                // 패널이 열린 사이 차량이 다시 연결돼 취소된 후보는 기록하지 않는다
                val candidate = dao.candidate(target.candidateId)?.takeIf { it.status == CandidateStatus.READY } ?: return@withLock null
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
                val existing = dao.latestRecord()?.takeIf { it.id == target.recordId } ?: return@withLock null
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
        record
    }

    data class PhotoResult(val attached: Boolean, val previousPath: String?)

    /** 홈 차량 카드에서 찍은 사진을 현재 기록에 붙인다 */
    // 사진·위치 수정은 행 전체를 다시 쓰므로, 뒤늦게 채워지는 하차 위치(attachLocation)와 같은 잠금 안에서 한다
    suspend fun setPhoto(recordId: String, path: String): PhotoResult = candidateLock.withLock {
        val existing = dao.latestRecord()?.takeIf { it.id == recordId } ?: return@withLock PhotoResult(false, null)
        dao.upsertRecord(existing.copy(photoPath = path))
        PhotoResult(true, existing.photoPath)
    }

    /** 사진 삭제. 화면에 보이던 사진이 아직 이 기록의 사진일 때만 지운다 */
    suspend fun clearPhoto(recordId: String, path: String): Boolean = candidateLock.withLock {
        val existing = dao.latestRecord()?.takeIf { it.id == recordId && it.photoPath == path } ?: return@withLock false
        dao.upsertRecord(existing.copy(photoPath = null))
        true
    }

    /** 홈의 위치 저장 아이콘. 현재 위치를 이 기록의 주차 위치로 저장한다 */
    suspend fun setLocation(recordId: String, fix: Fix): ParkingRecordEntity? = candidateLock.withLock {
        val existing = dao.latestRecord()?.takeIf { it.id == recordId } ?: return@withLock null
        val updated = existing.copy(
            latitude = fix.latitude,
            longitude = fix.longitude,
            locationAccuracyMeters = fix.accuracyMeters,
            locationCapturedAt = fix.capturedAtMs,
            locationSource = LocationSource.SAVED,
        )
        dao.upsertRecord(updated)
        updated
    }

    companion object {
        /** 연결 이벤트가 늦게 오는 경우를 감안한 여유 */
        const val RECONNECT_GRACE_MS = 3_000L
        const val HOME_RADIUS_M = 200.0

        /** 집 근처면 상태바 기본 켜짐, 그 외(집 미등록·위치 모름 포함) 기본 꺼짐 */
        fun isNearHome(homeLat: Double?, homeLng: Double?, lat: Double?, lng: Double?): Boolean {
            if (homeLat == null || homeLng == null || lat == null || lng == null) return false
            return FloorEstimator.distanceMeters(homeLat, homeLng, lat, lng) <= HOME_RADIUS_M
        }
    }
}
