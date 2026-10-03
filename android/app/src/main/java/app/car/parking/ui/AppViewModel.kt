package app.car.parking.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import app.car.parking.CarApp
import app.car.parking.data.storage.AppSettings
import app.car.parking.data.storage.AppThemeId
import app.car.parking.data.storage.CandidateEntity
import app.car.parking.data.storage.CandidateStatus
import app.car.parking.data.storage.DrawerSide
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.FloorRecommendation
import app.car.parking.domain.parking.DrawerTarget
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
    val pendingPhotoPath: String? = null,
    val existingPhotoPath: String? = null,
    val saving: Boolean = false,
    /** 자동 진입으로 열린 패널 */
    val autoEntry: Boolean = false,
)

enum class LaunchTestStatus { Idle, Waiting, Passed, NotInBackground, Failed }

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

    private val _launchTest = MutableStateFlow(LaunchTestStatus.Idle)
    val launchTest: StateFlow<LaunchTestStatus> = _launchTest.asStateFlow()

    private var recommendationJob: Job? = null

    fun refreshChecks() {
        _checks.value = SystemChecks.read(app, container.pressure.hasBarometer)
        viewModelScope.launch { detectLaunchTestFailure() }
    }

    // ── 자동 진입 ──────────────────────────────────────────────

    fun handleEntry(candidateId: String?, openPanel: Boolean, autoEntry: Boolean, launchTest: Boolean, wasBackground: Boolean) {
        viewModelScope.launch {
            if (launchTest) {
                if (wasBackground) {
                    val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
                    container.settings.markAutoLaunchTestPassed(System.currentTimeMillis(), device)
                    _launchTest.value = LaunchTestStatus.Passed
                } else {
                    _launchTest.value = LaunchTestStatus.NotInBackground
                    container.settings.clearAutoLaunchTestStarted()
                }
                return@launch
            }
            if (candidateId != null && openPanel) openCandidate(candidateId, autoEntry)
        }
    }

    private suspend fun openCandidate(candidateId: String, autoEntry: Boolean) {
        val candidate = container.parking.candidate(candidateId) ?: return
        if (candidate.status != CandidateStatus.READY) return
        AutoLauncher.cancelFallback(app)
        cleanupPendingPhoto()
        _drawer.value = DrawerUiState(
            open = true,
            target = DrawerTarget.Candidate(candidateId),
            recommendationPending = true,
            autoEntry = autoEntry,
        )
        // 추천 계산이 늦어도 패널은 먼저 연다
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            var current: CandidateEntity = candidate
            applyRecommendation(container.parking.recommendationFor(current, container.pressure.hasBarometer))
            val ageMs = System.currentTimeMillis() - current.detectedAt
            if (current.latitude == null && ageMs < LOCATION_ATTACH_WINDOW_MS) {
                container.location.currentFix()?.let { fix ->
                    container.parking.attachLocation(candidateId, fix)
                    current = container.parking.candidate(candidateId) ?: current
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

    // ── 패널 ──────────────────────────────────────────────────

    fun openFromFloorCard() {
        viewModelScope.launch {
            val candidate = pendingCandidate.value
            if (candidate != null) {
                openCandidate(candidate.id, autoEntry = false)
                return@launch
            }
            cleanupPendingPhoto()
            val existing = (record.value as? RecordState.Loaded)?.record
            _drawer.value = if (existing != null) {
                DrawerUiState(
                    open = true,
                    target = DrawerTarget.Edit(existing.id),
                    selectedLevel = existing.floorLevel,
                    existingPhotoPath = existing.photoPath,
                )
            } else {
                DrawerUiState(open = true, target = DrawerTarget.Manual)
            }
        }
    }

    fun selectLevel(level: Int) {
        _drawer.update { it.copy(selectedLevel = level, userTouched = true) }
    }

    fun setPendingPhoto(path: String) {
        val previous = _drawer.value.pendingPhotoPath
        if (previous != null && previous != path) container.photos.delete(previous)
        _drawer.update { it.copy(pendingPhotoPath = path) }
    }

    /** 닫기: 후보를 확정하지 않고 기존 확정 기록을 유지한다 */
    fun closeDrawer() {
        recommendationJob?.cancel()
        cleanupPendingPhoto()
        _drawer.update { DrawerUiState() }
    }

    private fun cleanupPendingPhoto() {
        _drawer.value.pendingPhotoPath?.let { container.photos.delete(it) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val state = _drawer.value
        val level = state.selectedLevel ?: return
        if (state.saving) return
        _drawer.update { it.copy(saving = true) }
        viewModelScope.launch {
            val manualFix = if (state.target == DrawerTarget.Manual) container.location.currentFix(5_000L) else null
            val previous = (record.value as? RecordState.Loaded)?.record
            val saved = container.parking.confirm(
                target = state.target,
                floorLevel = level,
                photoPath = state.pendingPhotoPath,
                manualFix = manualFix,
                vehicleId = settings.value?.registeredVehicleAddress,
            )
            if (saved != null) {
                if (state.target is DrawerTarget.Edit && state.pendingPhotoPath != null && previous?.photoPath != saved.photoPath) {
                    container.photos.delete(previous?.photoPath)
                }
                withContext(Dispatchers.Default) {
                    StatusBarNotifier.sync(app, settings.value?.statusBarEnabled == true, saved)
                }
            }
            _drawer.value = DrawerUiState()
            onSaved()
        }
    }

    // ── 설정 ──────────────────────────────────────────────────

    fun setTheme(theme: AppThemeId) = viewModelScope.launch { container.settings.setTheme(theme) }
    fun setDrawerSide(side: DrawerSide) = viewModelScope.launch { container.settings.setDrawerSide(side) }
    fun setAutoRecord(enabled: Boolean) = viewModelScope.launch { container.settings.setAutoRecord(enabled) }

    fun setStatusBar(enabled: Boolean) = viewModelScope.launch {
        container.settings.setStatusBar(enabled)
        StatusBarNotifier.sync(app, enabled, container.parking.latestRecordNow())
    }

    /** 알림 권한을 새로 받은 뒤 상태바 표시를 다시 게시한다 */
    fun resyncStatusBar() = viewModelScope.launch {
        StatusBarNotifier.sync(app, settings.value?.statusBarEnabled == true, container.parking.latestRecordNow())
    }

    fun setVehicle(address: String, name: String?) = viewModelScope.launch { container.settings.setVehicle(address, name) }
    fun completeOnboarding() = viewModelScope.launch { container.settings.setOnboardingCompleted() }

    /**
     * 자동 표시 테스트. 시작 후 사용자가 홈으로 나가면 실제 자동 진입과 같은 Activity 실행 경로로 앱을 띄운다.
     * Bluetooth 이벤트 수신 자체는 실제 차량 테스트로 따로 확인해야 한다.
     */
    fun startLaunchTest() {
        _launchTest.value = LaunchTestStatus.Waiting
        container.appScope.launch {
            container.settings.markAutoLaunchTestStarted(System.currentTimeMillis())
            delay(LAUNCH_TEST_DELAY_MS)
            val background = withContext(Dispatchers.Main) {
                !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
            if (!background) {
                container.settings.clearAutoLaunchTestStarted()
                _launchTest.value = LaunchTestStatus.NotInBackground
                return@launch
            }
            AutoLauncher.launchTest(app, wasBackground = true)
        }
    }

    private suspend fun detectLaunchTestFailure() {
        val started = container.settings.current().autoLaunchTestStartedAt
        if (started == 0L) return
        if (System.currentTimeMillis() - started > LAUNCH_TEST_DELAY_MS + LAUNCH_TEST_GRACE_MS) {
            container.settings.clearAutoLaunchTestStarted()
            _launchTest.value = LaunchTestStatus.Failed
        }
    }

    companion object {
        const val LAUNCH_TEST_DELAY_MS = 8_000L
        private const val LAUNCH_TEST_GRACE_MS = 7_000L
        private const val LOCATION_ATTACH_WINDOW_MS = 3 * 60 * 1000L
    }
}
