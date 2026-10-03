package app.car.parking.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.car.parking.CarApp
import app.car.parking.R
import app.car.parking.data.location.CurrentLocationState
import app.car.parking.data.location.Fix
import app.car.parking.data.location.UnavailableReason
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.components.rememberPhoto
import app.car.parking.ui.drawer.FloorDrawer
import app.car.parking.ui.home.HomeScreen
import app.car.parking.ui.onboarding.OnboardingScreen
import app.car.parking.ui.settings.SettingsScreen
import app.car.parking.ui.settings.UpdateDialog
import app.car.parking.ui.settings.rememberSystemActions
import app.car.parking.ui.theme.CarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class Screen { Onboarding, Home, Settings }

@Composable
fun CarRoot(viewModel: AppViewModel, onUnlockThen: (() -> Unit) -> Unit) {
    val settingsState by viewModel.settings.collectAsState()
    // 저장된 테마를 복원하기 전에는 그리지 않는다(창 배경은 흰색). 다른 테마가 잠깐 보이지 않게 한다
    val settings = settingsState ?: return
    val recordState by viewModel.record.collectAsState()
    val drawer by viewModel.drawer.collectAsState()
    val checks by viewModel.checks.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val locationSave by viewModel.locationSave.collectAsState()
    val update by viewModel.update.collectAsState()
    val updatePrompt by viewModel.updatePrompt.collectAsState()

    var screen by rememberSaveable { mutableStateOf(if (settings.onboardingCompleted) Screen.Home else Screen.Onboarding) }
    var settingsFocusReadiness by rememberSaveable { mutableStateOf(false) }
    var viewingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    // 자동 진입은 어떤 화면에서든 홈 + 패널로 연다
    LaunchedEffect(drawer.open) { if (drawer.open) screen = Screen.Home }
    // 알림 권한을 새로 받으면 상태바 표시를 다시 게시한다
    LaunchedEffect(checks.notificationsAllowed) { if (checks.notificationsAllowed) viewModel.resyncStatusBar() }

    CarTheme(settings.appTheme) {
        val actions = rememberSystemActions(viewModel::refreshChecks)
        when (screen) {
            Screen.Onboarding -> OnboardingScreen(
                settings = settings,
                checks = checks,
                onVehicle = { address, name -> viewModel.setVehicle(address, name) },
                onRefresh = viewModel::refreshChecks,
                onFinish = {
                    viewModel.completeOnboarding()
                    screen = Screen.Home
                },
            )
            Screen.Settings -> SettingsScreen(
                settings = settings,
                checks = checks,
                readiness = readiness,
                scrollToReadiness = settingsFocusReadiness,
                onBack = { screen = Screen.Home },
                onSave = { viewModel.saveSettings(it) },
                locateHere = viewModel::locateHere,
                onRefresh = viewModel::refreshChecks,
                update = update,
                onCheckUpdate = { viewModel.checkForUpdate() },
                onInstallUpdate = viewModel::downloadAndInstall,
            )
            Screen.Home -> {
                val record = (recordState as? RecordState.Loaded)?.record
                val location = rememberCurrentLocation(checks.anyLocation)
                val takePhoto = rememberTakePhoto(onUnlockThen, viewModel::attachPhoto)
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        record = record,
                        readiness = readiness,
                        location = location,
                        onOpenSettings = { settingsFocusReadiness = false; screen = Screen.Settings },
                        onOpenReadiness = { settingsFocusReadiness = true; screen = Screen.Settings },
                        onOpenFloor = viewModel::openFromFloorCard,
                        onOpenPhoto = { viewingPhoto = it },
                        onTakePhoto = takePhoto,
                        locationSave = locationSave,
                        onSaveLocation = {
                            when {
                                !checks.anyLocation -> actions.requestLocation()
                                !checks.locationServiceOn -> actions.openLocationSettings()
                                else -> viewModel.saveCurrentLocation()
                            }
                        },
                    )
                    if (drawer.open) {
                        BackHandler { viewModel.closeDrawer() }
                        FloorDrawer(
                            state = drawer,
                            side = settings.drawerSide,
                            // 사이드바 위치는 패널 손잡이를 길게 눌러 끌어서만 바꾼다
                            onSideChange = { viewModel.setDrawerSide(it) },
                            onSelect = viewModel::selectLevel,
                            onClose = viewModel::closeDrawer,
                            onSave = viewModel::save,
                            onStatusBarChange = viewModel::setDrawerStatusBar,
                            notificationsAllowed = checks.notificationsAllowed,
                            onRequestNotifications = actions::requestNotifications,
                        )
                    }
                }
            }
        }
        viewingPhoto?.let { PhotoViewer(it) { viewingPhoto = null } }
        if (updatePrompt && !drawer.open) {
            UpdateDialog(update, onInstall = viewModel::downloadAndInstall, onDismiss = viewModel::dismissUpdatePrompt)
        }
    }
}

/** 홈 차량 카드의 사진 찍기. 잠금 화면 위에서는 정상 시스템 인증 뒤 카메라를 연다 */
@Composable
private fun rememberTakePhoto(onUnlockThen: (() -> Unit) -> Unit, onTaken: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val photos = (context.applicationContext as CarApp).container.photos
    var captureTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = captureTarget
        captureTarget = null
        if (ok && photos.exists(path)) onTaken(path!!) else photos.delete(path)
    }
    return {
        onUnlockThen {
            val (file, uri) = photos.newCaptureTarget()
            captureTarget = file.absolutePath
            runCatching { capture.launch(uri) }.onFailure { photos.delete(file.absolutePath); captureTarget = null }
        }
    }
}

/** 홈이 보이는 동안만 위치 업데이트를 받는다. 화면을 벗어나면 요청을 해제한다 */
@Composable
private fun rememberCurrentLocation(permitted: Boolean): CurrentLocationState {
    val context = LocalContext.current
    val repo = (context.applicationContext as CarApp).container.location
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember { mutableStateOf<CurrentLocationState>(CurrentLocationState.Locating) }
    var lastFix by remember { mutableStateOf<Fix?>(null) }

    LaunchedEffect(permitted, lifecycle) {
        if (!permitted) {
            state = CurrentLocationState.Unavailable(UnavailableReason.NoPermission)
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!repo.isLocationEnabled()) {
                state = CurrentLocationState.Unavailable(UnavailableReason.LocationOff)
                return@repeatOnLifecycle
            }
            if (lastFix == null) state = CurrentLocationState.Locating
            launch {
                // 일정 시간 좌표가 없으면 위치 없음, 오래된 좌표는 Stale로 낮춘다
                delay(20_000L)
                while (true) {
                    val fix = lastFix
                    state = if (fix == null) CurrentLocationState.Unavailable(UnavailableReason.NoFix)
                    else CurrentLocationState.classify(fix, System.currentTimeMillis())
                    delay(10_000L)
                }
            }
            repo.updates().collect { fix ->
                lastFix = fix
                state = CurrentLocationState.classify(fix, System.currentTimeMillis())
            }
        }
    }
    return state
}

/** 주차 사진을 큰 모달 창으로 확인한다 */
@Composable
private fun PhotoViewer(path: String, onDismiss: () -> Unit) {
    val t = app.car.parking.ui.theme.LocalCarTokens.current
    val photo = rememberPhoto(path, maxSidePx = 2048)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .clip(t.cardShape)
                .background(Color.Black),
        ) {
            if (photo != null) {
                Image(photo, contentDescription = "주차 사진", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else if (!File(path).exists()) {
                androidx.compose.material3.Text("사진을 찾을 수 없어요", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            IconTarget(
                R.drawable.ic_close,
                "닫기",
                onDismiss,
                tint = Color.White,
                background = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    }
}
