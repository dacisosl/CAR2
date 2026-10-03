package app.car.parking.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.car.parking.BuildConfig
import app.car.parking.CarApp
import app.car.parking.data.location.Fix
import app.car.parking.data.storage.AppSettings
import app.car.parking.data.storage.AppThemeId
import app.car.parking.data.storage.CandidateEntity
import app.car.parking.data.storage.CandidateStatus
import app.car.parking.data.storage.DrawerSide
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.FloorRecommendation
import app.car.parking.domain.floor.Floors
import app.car.parking.domain.parking.DrawerTarget
import app.car.parking.domain.parking.ParkingRepository
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.platform.permissions.AutoRecordState
import app.car.parking.platform.permissions.Readiness
import app.car.parking.platform.permissions.SystemChecks
import app.car.parking.platform.statusbar.StatusBarNotifier
import app.car.parking.platform.update.UpdateInfo
import java.io.File
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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** 설치 화면을 열 수 있는 상태. 설치 허용이 없으면 먼저 설정으로 안내한다 */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
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

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** 앱을 열 때 자동으로 확인해 새 버전이 있으면 알려 준다(6시간에 한 번) */
    private val _updatePrompt = MutableStateFlow(false)
    val updatePrompt: StateFlow<Boolean> = _updatePrompt.asStateFlow()

    init {
        val prefs = app.getSharedPreferences("update", android.content.Context.MODE_PRIVATE)
        val last = prefs.getLong("lastCheck", 0L)
        // Play 배포판은 Play가 업데이트를 맡으므로 확인하지 않는다
        if (BuildConfig.SELF_UPDATE && System.currentTimeMillis() - last > AUTO_CHECK_INTERVAL_MS) {
            checkForUpdate(manual = false) {
                // 네트워크 실패는 다음 실행 때 다시 확인하도록 성공한 경우만 기록한다
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            }
        }
    }

    fun checkForUpdate(manual: Boolean = true, onChecked: () -> Unit = {}) {
        if (!BuildConfig.SELF_UPDATE) return
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = runCatching { container.updater.checkLatest() }.fold(
                onSuccess = { info ->
                    onChecked()
                    if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
                },
                onFailure = {
                    android.util.Log.w("AppUpdater", "update check failed", it)
                    UpdateState.Failed("업데이트를 확인하지 못했어요. 인터넷 연결을 확인하세요")
                },
            )
            val result = _update.value
            if (!manual) {
                if (result is UpdateState.Available) _updatePrompt.value = true else _update.value = UpdateState.Idle
            }
        }
    }

    fun dismissUpdatePrompt() {
        _updatePrompt.value = false
    }

    /** 내려받고(SHA-256·패키지 확인) 바로 설치 화면을 연다 */
    fun downloadAndInstall() {
        val info = when (val s = _update.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Ready -> return installReady()
            is UpdateState.Failed -> return
            else -> return
        }
        _update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            _update.value = runCatching {
                container.updater.download(info) { p -> _update.value = UpdateState.Downloading(info, p) }
            }.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed(it.message ?: "내려받지 못했어요") },
            )
            installReady()
        }
    }

    /** 설치 허용 화면에서 돌아오면 이어서 설치 화면을 연다 */
    private var awaitingInstallPermission = false

    private fun installReady() {
        val ready = _update.value as? UpdateState.Ready ?: return
        if (!container.updater.canInstall()) {
            awaitingInstallPermission = true
            container.updater.openInstallPermissionSettings()
            return
        }
        awaitingInstallPermission = false
        runCatching { container.updater.install(ready.file) }
    }

    fun refreshChecks() {
        _checks.value = SystemChecks.read(app, container.pressure.hasBarometer)
        if (awaitingInstallPermission && container.updater.canInstall()) installReady()
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
            val suggested = (rec as? FloorRecommendation.Suggested)?.level?.takeIf { it in Floors.reel() }
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

    /**
     * 패널 바깥·뒤로 가기: 릴에서 마지막으로 멈춘 층으로 저장하고 닫는다.
     * 고른 층이 없거나 기존 기록에서 바뀐 것이 없으면 저장하지 않고 닫는다.
     */
    fun dismissDrawer() {
        val state = _drawer.value
        if (state.saving) return
        val level = state.selectedLevel ?: return closeDrawer()
        val existing = (record.value as? RecordState.Loaded)?.record
        val target = state.target
        if (target is DrawerTarget.Edit && existing != null && existing.id == target.recordId &&
            existing.floorLevel == level && existing.statusBarShown == state.statusBarOn
        ) return closeDrawer()
        save()
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
            val result = container.parking.setPhoto(current.id, path)
            if (!result.attached) {
                container.photos.delete(path)
            } else if (result.previousPath != null && result.previousPath != path) {
                container.photos.delete(result.previousPath)
            }
        }
    }

    /** 주차 사진 삭제: 기록에서 떼어 낸 뒤 파일도 지운다 */
    fun deletePhoto(path: String) {
        val current = (record.value as? RecordState.Loaded)?.record ?: return
        viewModelScope.launch {
            if (container.parking.clearPhoto(current.id, path)) container.photos.delete(path)
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
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val LOCATION_ATTACH_WINDOW_MS = 3 * 60 * 1000L
    }
}
