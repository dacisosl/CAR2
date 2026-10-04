package app.car.parking.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.car.parking.BuildConfig
import app.car.parking.CarApp
import app.car.parking.data.location.Fix
import app.car.parking.data.storage.AppSettings
import app.car.parking.data.bluetooth.VehicleLink
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrawerUiState(
    val open: Boolean = false,
    val target: DrawerTarget = DrawerTarget.Manual,
    val selectedLevel: Int? = null,
    /** 릴 중앙(선택 띠)에 있는 층. 아직 고르지 않았을 때 저장 버튼이 이 층으로 저장한다 */
    val centerLevel: Int? = null,
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

/** 홈 상단의 차량 상태 한 줄. 자동 기록 준비 상태(아이콘)와 별개로 지금 무슨 일이 있는지 보여 준다 */
sealed interface VehicleStatus {
    /** 등록 차량이 연결돼 있음 — 이동 중 */
    data class Driving(val sinceMs: Long) : VehicleStatus
    /** 해제를 감지해 층수 기록을 기다리는 후보가 있음 */
    data class Exited(
        val candidateId: String,
        val detectedAtMs: Long,
        /** 하차 직후 붙인 위치. 아직 없으면 null */
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) : VehicleStatus
    /** 보여 줄 것 없음 */
    data object Idle : VehicleStatus
}

/** 레코드가 아직 로드되지 않은 상태와 기록 없음 상태를 구분한다 */
sealed interface RecordState {
    data object Loading : RecordState
    data class Loaded(val record: ParkingRecordEntity?) : RecordState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CarApp
    private val container = app.container

    // 자동 진입처럼 프로세스가 이미 설정을 읽었으면 그 값으로 첫 프레임을 바로 그린다(빈 프레임 없음)
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.settings.cached)

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

    /** 이동 중(연결) > 하차 감지(표시 대상 후보) > 없음 */
    val vehicleStatus: StateFlow<VehicleStatus> = combine(settings, pendingCandidate, readiness) { s, candidate, ready ->
        when {
            s?.registeredVehicleAddress == null || ready == AutoRecordState.Unsupported -> VehicleStatus.Idle
            // 연결 기록보다 새 하차 후보가 있으면 하차로 본다(연결 해제 저장이 늦게 반영되는 첫 화면 보호)
            s.vehicleConnected && (candidate == null || s.lastVehicleEventAt > candidate.detectedAt) ->
                VehicleStatus.Driving(s.lastVehicleEventAt)
            candidate != null -> VehicleStatus.Exited(candidate.id, candidate.detectedAt, candidate.latitude, candidate.longitude)
            else -> VehicleStatus.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VehicleStatus.Idle)

    private val _drawer = MutableStateFlow(DrawerUiState())
    val drawer: StateFlow<DrawerUiState> = _drawer.asStateFlow()

    private val _locationSave = MutableStateFlow(LocationSaveStatus.Idle)
    val locationSave: StateFlow<LocationSaveStatus> = _locationSave.asStateFlow()

    private var drawerJob: Job? = null

    /** 하차 위치 확인 작업. 패널 저장·닫기(drawerJob 취소)와 무관하게 끝까지 진행한다 */
    private var disconnectFixJob: Job? = null
    private var disconnectFixCandidateId: String? = null

    /** 시스템 상태를 마지막으로 읽은 시각. 화면이 막 열렸을 때 같은 바인더 호출을 반복하지 않는다 */
    private var checksReadAt = android.os.SystemClock.elapsedRealtime()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** 앱을 열 때 자동으로 확인해 새 버전이 있으면 알려 준다(6시간에 한 번) */
    private val _updatePrompt = MutableStateFlow(false)
    val updatePrompt: StateFlow<Boolean> = _updatePrompt.asStateFlow()

    init {
        // 시동만 껐다 켜서 후보가 취소되면, 그 후보로 열린 패널을 저장 없이 닫는다
        viewModelScope.launch {
            pendingCandidate.collect { pending ->
                val state = _drawer.value
                val target = state.target
                if (state.open && !state.saving && target is DrawerTarget.Candidate && pending?.id != target.candidateId) {
                    val current = container.parking.candidate(target.candidateId)
                    if (current?.status == CandidateStatus.CANCELLED) closeDrawer()
                }
            }
        }
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
        val updater = container.updater ?: return
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = runCatching { updater.checkLatest() }.fold(
                onSuccess = { info ->
                    onChecked()
                    if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
                },
                onFailure = {
                    android.util.Log.w("SelfUpdate", "update check failed", it)
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
        val updater = container.updater ?: return
        _update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            _update.value = runCatching {
                updater.download(info) { p -> _update.value = UpdateState.Downloading(info, p) }
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
        val updater = container.updater ?: return
        if (!updater.canInstall()) {
            awaitingInstallPermission = true
            updater.openInstallPermissionSettings()
            return
        }
        awaitingInstallPermission = false
        runCatching { updater.install(ready.file) }
    }

    fun refreshChecks() {
        _checks.value = SystemChecks.read(app, container.pressure.hasBarometer)
        checksReadAt = android.os.SystemClock.elapsedRealtime()
        if (awaitingInstallPermission && container.updater?.canInstall() == true) installReady()
    }

    /** 자동 표시로 열리지 않은 후보를 사용자가 앱을 열 때 한 번만 올린다(닫으면 다시 올리지 않음) */
    private var surfacedCandidateId: String? = null

    /**
     * 화면이 앞으로 올 때마다: 저장된 연결 상태를 실제 프로필 연결로 다시 확인하고(놓친 이벤트 보정),
     * 오래된 후보는 정리하며, 아직 보여 주지 않은 하차 후보가 있으면 패널을 연다.
     */
    fun onForeground() {
        // ViewModel을 막 만든 직후(init에서 방금 읽음)에는 메인 스레드 바인더 호출을 반복하지 않는다
        if (android.os.SystemClock.elapsedRealtime() - checksReadAt > 1_000L) refreshChecks()
        viewModelScope.launch {
            val s = settings.value ?: container.settings.current()
            val address = s.registeredVehicleAddress ?: return@launch
            val now = System.currentTimeMillis()
            container.parking.expireStaleCandidates(address, now, STALE_CANDIDATE_MS)
            // 프로필 연결 조회는 메인 스레드 밖에서(화면이 막 열리는 동안 첫 프레임을 막지 않게)
            withContext(Dispatchers.Default) { VehicleLink.isConnected(app, address) }?.let { connected ->
                // 연결로 바뀐 것을 늦게 알았으면 지금부터 운전 시간을 센다.
                // 방금(30초 안) 받은 연결 이벤트는 오디오 프로필이 아직 안 붙었을 수 있어 ‘끊김’으로 덮지 않는다
                val freshConnect = s.vehicleConnected && now - s.lastVehicleEventAt in 0 until LINK_GRACE_MS
                if (connected != s.vehicleConnected && (connected || !freshConnect)) {
                    container.settings.setVehicleLink(connected, if (connected) now else 0L)
                }
            }
            if (_drawer.value.open) return@launch
            val pending = container.parking.pendingCandidate.first() ?: return@launch
            if (pending.id == surfacedCandidateId) return@launch
            surfacedCandidateId = pending.id
            openCandidate(pending.id, autoEntry = false)
        }
    }

    // ── 자동 진입 ──────────────────────────────────────────────

    /**
     * 자동 진입 패널을 여는 중. MainActivity.onCreate가 setContent 전에 handleEntry를 부르므로
     * 첫 화면 구성 때 이미 true다. 이 동안 홈은 무거운 지도 생성을 잠깐 미룬다.
     */
    private val _entryPending = MutableStateFlow(false)
    val entryPending: StateFlow<Boolean> = _entryPending.asStateFlow()
    private val autoEntryPending = MutableStateFlow(false)

    /** 잠금 화면 위 표시·화면 켜기: 자동 진입을 여는 중이거나 자동 진입 패널이 떠 있는 동안만 */
    val lockScreenEntry: Flow<Boolean>
        get() = combine(autoEntryPending, drawer) { pending, d -> pending || (d.open && d.autoEntry) }.distinctUntilChanged()

    fun handleEntry(candidateId: String?, openPanel: Boolean, autoEntry: Boolean) {
        if (candidateId == null || !openPanel) return
        _entryPending.value = true
        if (autoEntry) autoEntryPending.value = true
        viewModelScope.launch {
            try {
                openCandidate(candidateId, autoEntry)
            } finally {
                _entryPending.value = false
                autoEntryPending.value = false
            }
        }
    }

    private suspend fun openCandidate(candidateId: String, autoEntry: Boolean) {
        // 저장 중인 패널은 저장이 끝날 때까지 다른 패널로 바꾸지 않는다
        if (_drawer.value.saving) return
        val candidate = container.parking.candidate(candidateId) ?: return
        if (candidate.status != CandidateStatus.READY) return
        surfacedCandidateId = candidateId
        AutoLauncher.cancelFallback(app)
        _drawer.value = DrawerUiState(
            open = true,
            target = DrawerTarget.Candidate(candidateId),
            recommendationPending = true,
            statusBarOn = nearHome(candidate.latitude, candidate.longitude),
            autoEntry = autoEntry,
        )
        // 하차 위치는 패널과 별도 작업으로 끝까지 받는다. 저장·닫기로 drawerJob이 취소돼도 위치는 기록에 남는다
        val locating = captureDisconnectLocation(candidate)
        // 추천 계산이 늦어도 패널은 먼저 연다
        drawerJob?.cancel()
        drawerJob = viewModelScope.launch {
            applyRecommendation(container.parking.recommendationFor(candidate, container.pressure.hasBarometer))
            if (locating != null) {
                // drawerJob이 취소되면 기다림만 멈추고 위치 작업(appScope)은 계속된다
                locating.join()
                val current = container.parking.candidate(candidateId) ?: candidate
                if (current.latitude != null) {
                    applyHomeDefault(current.latitude, current.longitude)
                    applyRecommendation(container.parking.recommendationFor(current, container.pressure.hasBarometer))
                }
            }
            _drawer.update { it.copy(recommendationPending = false) }
        }
    }

    /** 해제 직후 위치를 한 번 받아 후보(또는 이미 저장된 기록)에 붙인다. 같은 후보의 진행 중 작업은 재사용한다 */
    private fun captureDisconnectLocation(candidate: CandidateEntity): Job? {
        if (candidate.latitude != null) return null
        if (System.currentTimeMillis() - candidate.detectedAt >= LOCATION_ATTACH_WINDOW_MS) return null
        disconnectFixJob?.takeIf { it.isActive && disconnectFixCandidateId == candidate.id }?.let { return it }
        disconnectFixCandidateId = candidate.id
        return container.appScope.launch {
            val fix = container.location.currentFix() ?: return@launch
            container.parking.attachLocation(candidate.id, fix)
        }.also { disconnectFixJob = it }
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
        if (_drawer.value.saving) return
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

    // 저장 중에는 이미 기록한 값과 화면이 어긋나지 않게 패널 입력을 받지 않는다
    fun selectLevel(level: Int) {
        _drawer.update { if (it.saving) it else it.copy(selectedLevel = level, userTouched = true) }
    }

    fun setDrawerStatusBar(on: Boolean) {
        _drawer.update { if (it.saving) it else it.copy(statusBarOn = on, statusBarTouched = true) }
    }

    /** 닫기: 후보를 확정하지 않고 기존 확정 기록을 유지한다 */
    fun setCenterLevel(level: Int) {
        if (_drawer.value.centerLevel != level) _drawer.update { it.copy(centerLevel = level) }
    }

    fun closeDrawer() {
        if (_drawer.value.saving) return
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
        // 저장 버튼은 명시적인 확인이므로, 아직 릴을 움직이지 않았으면 띠에 있는 층으로 저장한다
        val level = state.selectedLevel ?: state.centerLevel ?: return
        if (state.saving) return
        // 띠에 있던 층으로 저장하는 경우에도 선택값을 저장한 층으로 맞춰, 저장 중 릴·표지판이 기록과 같은 층을 가리키게 한다
        _drawer.update { it.copy(saving = true, selectedLevel = level) }
        drawerJob?.cancel()
        val vehicleId = settings.value?.registeredVehicleAddress
        viewModelScope.launch {
            // 기록은 바로 남긴다. 화면이 닫히거나 앱이 정리돼도 끝까지 쓰도록 앱 범위에서 실행한다
            container.appScope.async {
                val manualFix = if (state.target == DrawerTarget.Manual) container.location.currentFix(5_000L) else null
                container.parking.confirm(
                    target = state.target,
                    floorLevel = level,
                    photoPath = null,
                    manualFix = manualFix,
                    vehicleId = vehicleId,
                    statusBar = state.statusBarOn,
                )
                withContext(Dispatchers.Default) { StatusBarNotifier.refresh(app) }
            }.await()
            // 잠금 화면 위에서 닫자마자 백그라운드가 되면 위치를 못 받을 수 있어, 진행 중인 하차 위치를 잠깐(최대 2초)
            // 화면을 띄운 채 기다린다. 위치는 도착하는 대로 저장된 기록에 채워진다(attachLocation)
            val candidateId = (state.target as? DrawerTarget.Candidate)?.candidateId
            disconnectFixJob?.takeIf { candidateId != null && it.isActive && disconnectFixCandidateId == candidateId }
                ?.let { job -> kotlinx.coroutines.withTimeoutOrNull(2_000L) { job.join() } }
            // 이 저장의 패널만 닫는다
            _drawer.update { if (it.saving && it.target == state.target) DrawerUiState() else it }
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
        StatusBarNotifier.refresh(app)
    }

    fun setVehicle(address: String, name: String?) = viewModelScope.launch { container.settings.setVehicle(address, name) }
    fun completeOnboarding() = viewModelScope.launch { container.settings.setOnboardingCompleted() }

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val LOCATION_ATTACH_WINDOW_MS = 3 * 60 * 1000L
        /** 이보다 오래 기록하지 않은 하차 후보는 지난 주차로 보고 정리한다 */
        private const val STALE_CANDIDATE_MS = 12 * 60 * 60 * 1000L

        /** 연결 이벤트 직후 오디오 프로필이 붙기까지 기다려 주는 시간 */
        private const val LINK_GRACE_MS = 30_000L
    }
}
