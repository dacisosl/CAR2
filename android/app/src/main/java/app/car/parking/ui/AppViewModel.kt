package app.car.parking.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.car.parking.CarApp
import app.car.parking.data.location.Fix
import app.car.parking.data.storage.AppSettings
import app.car.parking.data.storage.AppThemeId
import app.car.parking.data.storage.CandidateEntity
import app.car.parking.data.storage.CandidateStatus
import app.car.parking.data.storage.DrawerSide
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.FloorRecommendation
import app.car.parking.domain.parking.DrawerTarget
import app.car.parking.domain.parking.ParkingRepository
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.platform.permissions.AutoRecordState
import app.car.parking.platform.permissions.Readiness
import app.car.parking.platform.permissions.SystemChecks
import app.car.parking.platform.statusbar.StatusBarNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrawerUiState(
    val open: Boolean = false,
    val target: DrawerTarget = DrawerTarget.Manual,
    val selectedLevel: Int? = null,
    /** 사용자가 직접 릴을 움직였으면 늦게 온 추천으로 덮어쓰지 않는다 */
    val userTouched: Boolean = false,
    val recommendation: FloorRecommendation? = null,
    val recommendationPending: Boolean = false,
    /** 이 기록을 상태바에 표시할지. 집 근처면 기본 켜짐, 그 외 기본 꺼짐 */
    val statusBarOn: Boolean = false,
    /** 사용자가 스위치를 만졌으면 위치가 늦게 확인돼도 기본값으로 바꾸지 않는다 */
    val statusBarTouched: Boolean = false,
    val saving: Boolean = false,
    /** 자동 진입으로 열린 패널 */
    val autoEntry: Boolean = false,
)

/** 설정 화면에서 고친 뒤 저장 버튼으로 확정하는 값 */
data class SettingsDraft(
    val theme: AppThemeId,
    val vehicleAddress: String?,
    val vehicleName: String?,
    val homeLatitude: Double?,
    val homeLongitude: Double?,
) {
    companion object {
        fun from(s: AppSettings) = SettingsDraft(
            theme = s.appTheme,
            vehicleAddress = s.registeredVehicleAddress,
            vehicleName = s.registeredVehicleName,
            homeLatitude = s.homeLatitude,
            homeLongitude = s.homeLongitude,
        )
    }
}

enum class LocationSaveStatus { Idle, Saving, Saved, Failed }

/** 레코드가 아직 로드되지 않은 상태와 기록 없음 상태를 구분한다 */
sealed interface RecordState {
    data object Loading : RecordState
    data class Loaded(val record: ParkingRecordEntity?) : RecordState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CarApp
    private val container = app.container

    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val record: StateFlow<RecordState> = container.parking.latestRecord
        .map<ParkingRecordEntity?, RecordState> { RecordState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordState.Loading)

    val pendingCandidate: StateFlow<CandidateEntity?> = container.parking.pendingCandidate
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _checks = MutableStateFlow(SystemChecks.read(app, container.pressure.hasBarometer))
    val checks: StateFlow<SystemChecks> = _checks.asStateFlow()

    val readiness: StateFlow<AutoRecordState> = combine(settings, checks) { s, c ->
        if (s == null) AutoRecordState.MonitoringStopped else Readiness.state(s, c)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AutoRecordState.MonitoringStopped)

    private val _drawer = MutableStateFlow(DrawerUiState())
    val drawer: StateFlow<DrawerUiState> = _drawer.asStateFlow()

    private val _locationSave = MutableStateFlow(LocationSaveStatus.Idle)
    val locationSave: StateFlow<LocationSaveStatus> = _locationSave.asStateFlow()

    private var drawerJob: Job? = null

    fun refreshChecks() {
        _checks.value = SystemChecks.read(app, container.pressure.hasBarometer)
    }

    // ── 자동 진입 ──────────────────────────────────────────────

    fun handleEntry(candidateId: String?, openPanel: Boolean, autoEntry: Boolean) {
        if (candidateId == null || !openPanel) return
        viewModelScope.launch { openCandidate(candidateId, autoEntry) }
    }

    private suspend fun openCandidate(candidateId: String, autoEntry: Boolean) {
        val candidate = container.parking.candidate(candidateId) ?: return
        if (candidate.status != CandidateStatus.READY) return
        AutoLauncher.cancelFallback(app)
        _drawer.value = DrawerUiState(
            open = true,
            target = DrawerTarget.Candidate(candidateId),
            recommendationPending = true,
            statusBarOn = nearHome(candidate.latitude, candidate.longitude),
            autoEntry = autoEntry,
        )
        // 추천 계산이 늦어도 패널은 먼저 연다
        drawerJob?.cancel()
        drawerJob = viewModelScope.launch {
            var current: CandidateEntity = candidate
            applyRecommendation(container.parking.recommendationFor(current, container.pressure.hasBarometer))
            val ageMs = System.currentTimeMillis() - current.detectedAt
            if (current.latitude == null && ageMs < LOCATION_ATTACH_WINDOW_MS) {
                container.location.currentFix()?.let { fix ->
                    container.parking.attachLocation(candidateId, fix)
                    current = container.parking.candidate(candidateId) ?: current
                    applyHomeDefault(current.latitude, current.longitude)
                    applyRecommendation(container.parking.recommendationFor(current, container.pressure.hasBarometer))
                }
            }
            _drawer.update { it.copy(recommendationPending = false) }
        }
    }

    private fun applyRecommendation(rec: FloorRecommendation) {
        _drawer.update { state ->
            if (!state.open) return@update state
            val suggested = (rec as? FloorRecommendation.Suggested)?.level
            state.copy(
                recommendation = rec,
                selectedLevel = if (!state.userTouched && suggested != null) suggested else state.selectedLevel,
            )
        }
    }

    private fun nearHome(lat: Double?, lng: Double?): Boolean {
        val s = settings.value ?: return false
        return ParkingRepository.isNearHome(s.homeLatitude, s.homeLongitude, lat, lng)
    }

    private fun applyHomeDefault(lat: Double?, lng: Double?) {
        _drawer.update { if (it.open && !it.statusBarTouched) it.copy(statusBarOn = nearHome(lat, lng)) else it }
    }

    // ── 패널 ──────────────────────────────────────────────────

    fun openFromFloorCard() {
        viewModelScope.launch {
            val candidate = pendingCandidate.value
            if (candidate != null) {
                openCandidate(candidate.id, autoEntry = false)
                return@launch
            }
            drawerJob?.cancel()
            val existing = (record.value as? RecordState.Loaded)?.record
            if (existing != null) {
                _drawer.value = DrawerUiState(
                    open = true,
                    target = DrawerTarget.Edit(existing.id),
                    selectedLevel = existing.floorLevel,
                    // 기존 기록은 이전에 고른 상태바 선택을 그대로 보여준다
                    statusBarOn = existing.statusBarShown,
                    statusBarTouched = true,
                )
            } else {
                _drawer.value = DrawerUiState(open = true, target = DrawerTarget.Manual)
                // 직접 기록은 지금 위치로 집 근처인지 판단한다
                drawerJob = viewModelScope.launch {
                    container.location.currentFix(5_000L)?.let { applyHomeDefault(it.latitude, it.longitude) }
                }
            }
        }
    }

    fun selectLevel(level: Int) {
        _drawer.update { it.copy(selectedLevel = level, userTouched = true) }
    }

    fun setDrawerStatusBar(on: Boolean) {
        _drawer.update { it.copy(statusBarOn = on, statusBarTouched = true) }
    }

    /** 닫기: 후보를 확정하지 않고 기존 확정 기록을 유지한다 */
    fun closeDrawer() {
        drawerJob?.cancel()
        _drawer.update { DrawerUiState() }
    }

    fun save() {
        val state = _drawer.value
        val level = state.selectedLevel ?: return
        if (state.saving) return
        _drawer.update { it.copy(saving = true) }
        drawerJob?.cancel()
        viewModelScope.launch {
            val manualFix = if (state.target == DrawerTarget.Manual) container.location.currentFix(5_000L) else null
            val saved = container.parking.confirm(
                target = state.target,
                floorLevel = level,
                photoPath = null,
                manualFix = manualFix,
                vehicleId = settings.value?.registeredVehicleAddress,
                statusBar = state.statusBarOn,
            )
            withContext(Dispatchers.Default) { StatusBarNotifier.sync(app, saved ?: container.parking.latestRecordNow()) }
            _drawer.value = DrawerUiState()
        }
    }

    // ── 홈: 사진·위치 ──────────────────────────────────────────

    fun attachPhoto(path: String) {
        val current = (record.value as? RecordState.Loaded)?.record
        if (current == null) {
            container.photos.delete(path)
            return
        }
        viewModelScope.launch {
            val previous = container.parking.setPhoto(current.id, path)
            if (previous != null && previous != path) container.photos.delete(previous)
        }
    }

    /** 위치 저장 아이콘: 지금 위치를 현재 기록의 주차 위치로 저장한다 */
    fun saveCurrentLocation() {
        val current = (record.value as? RecordState.Loaded)?.record ?: return
        if (_locationSave.value == LocationSaveStatus.Saving) return
        _locationSave.value = LocationSaveStatus.Saving
        viewModelScope.launch {
            val fix: Fix? = container.location.currentFix(10_000L)
            _locationSave.value = if (fix != null && container.parking.setLocation(current.id, fix) != null) {
                LocationSaveStatus.Saved
            } else {
                LocationSaveStatus.Failed
            }
            delay(3_000L)
            _locationSave.value = LocationSaveStatus.Idle
        }
    }

    /** 위치 관리의 ‘현재 위치로 등록’. 결과는 설정 화면 초안에 넣고 저장 버튼으로 확정한다 */
    suspend fun locateHere(): Fix? = container.location.currentFix(10_000L)

    // ── 설정 ──────────────────────────────────────────────────

    /** 설정 화면 우측 상단 저장 */
    fun saveSettings(draft: SettingsDraft) = viewModelScope.launch {
        val current = settings.value
        if (current?.appTheme != draft.theme) container.settings.setTheme(draft.theme)
        if (draft.vehicleAddress != null && draft.vehicleAddress != current?.registeredVehicleAddress) {
            container.settings.setVehicle(draft.vehicleAddress, draft.vehicleName)
        }
        if (draft.homeLatitude != null && draft.homeLongitude != null) {
            if (draft.homeLatitude != current?.homeLatitude || draft.homeLongitude != current.homeLongitude) {
                container.settings.setHome(draft.homeLatitude, draft.homeLongitude)
            }
        } else if (current?.hasHome == true) {
            container.settings.clearHome()
        }
    }

    fun setDrawerSide(side: DrawerSide) = viewModelScope.launch { container.settings.setDrawerSide(side) }

    /** 알림 권한을 새로 받은 뒤 상태바 표시를 다시 게시한다 */
    fun resyncStatusBar() = viewModelScope.launch {
        StatusBarNotifier.sync(app, container.parking.latestRecordNow())
    }

    fun setVehicle(address: String, name: String?) = viewModelScope.launch { container.settings.setVehicle(address, name) }
    fun completeOnboarding() = viewModelScope.launch { container.settings.setOnboardingCompleted() }

    companion object {
        private const val LOCATION_ATTACH_WINDOW_MS = 3 * 60 * 1000L
    }
}
