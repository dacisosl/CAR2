package app.car.parking.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.car.parking.ui.theme.CarType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    val vehicleStatus by viewModel.vehicleStatus.collectAsState()
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
        SystemBarAppearance(dark = app.car.parking.ui.theme.LocalCarTokens.current.dark)
        val actions = rememberSystemActions(viewModel::refreshChecks)
        // 홈 카드와 사진 모달(다시 찍기)이 같은 촬영 흐름을 쓴다
        val takePhoto = rememberTakePhoto(onUnlockThen, viewModel::attachPhoto)
        // 자동 진입 패널은 설정·첫 시작 화면에 있었더라도 같은 프레임에 홈 위에 연다
        when (if (drawer.open) Screen.Home else screen) {
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
                Box(Modifier.fillMaxSize()) {
                    // 자동 진입으로 홈이 처음 그려질 때는 패널(주차 영상)을 먼저 보여 주고,
                    // 지도는 패널이 닫히거나 영상이 끝난 뒤 만든다(지도 생성이 영상 도중 화면을 멈추지 않게)
                    val deferStart = remember { viewModel.entryPending.value || (drawer.open && drawer.autoEntry) }
                    val entryPending by viewModel.entryPending.collectAsState()
                    val deferMap = deferStart && (entryPending || drawer.open)
                    HomeScreen(
                        deferMap = deferMap,
                        record = record,
                        readiness = readiness,
                        vehicleStatus = vehicleStatus,
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
                        BackHandler { viewModel.dismissDrawer() }
                        FloorDrawer(
                            state = drawer,
                            // 사이드바 위치는 설정(디자인 설정 → 사이드바 위치)에서만 바꾼다
                            side = settings.drawerSide,
                            onSelect = viewModel::selectLevel,
                            onCenter = viewModel::setCenterLevel,
                            onClose = viewModel::closeDrawer,
                            onDismiss = viewModel::dismissDrawer,
                            onSave = viewModel::save,
                            onStatusBarChange = viewModel::setDrawerStatusBar,
                            notificationsAllowed = checks.notificationsAllowed,
                            onRequestNotifications = actions::requestNotifications,
                        )
                    }
                }
            }
        }
        viewingPhoto?.let { path ->
            PhotoViewer(
                path,
                onDismiss = { viewingPhoto = null },
                onRetake = { viewingPhoto = null; takePhoto() },
                onDelete = { viewingPhoto = null; viewModel.deletePhoto(path) },
            )
        }
        if (updatePrompt && !drawer.open) {
            UpdateDialog(update, onInstall = viewModel::downloadAndInstall, onDismiss = viewModel::dismissUpdatePrompt)
        }
    }
}

/** 상태바·내비게이션 아이콘 명암. 밝은 테마는 어두운 아이콘, UHD는 밝은 아이콘. 시스템 다크 모드는 따르지 않는다 */
@Composable
private fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
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
            // 다시 앞으로 왔을 때 지난 세션의 좌표를 현재 위치로 쓰지 않는다(2분이 지난 좌표는 Stale)
            state = lastFix?.let { CurrentLocationState.classify(it, System.currentTimeMillis()) } ?: CurrentLocationState.Locating
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

/** 주차 사진을 큰 모달 창으로 확인한다. 아래에서 다시 찍거나 삭제(확인 후)할 수 있다 */
@Composable
private fun PhotoViewer(path: String, onDismiss: () -> Unit, onRetake: () -> Unit, onDelete: () -> Unit) {
    val t = app.car.parking.ui.theme.LocalCarTokens.current
    val photo = rememberPhoto(path, maxSidePx = 2048)
    var confirmDelete by remember { mutableStateOf(false) }
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
                Text("사진을 찾을 수 없어요", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            IconTarget(
                R.drawable.ic_close,
                "닫기",
                onDismiss,
                tint = Color.White,
                background = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PhotoAction(R.drawable.ic_camera, "다시 찍기", Modifier.weight(1f), onRetake)
                PhotoAction(R.drawable.ic_delete, "삭제", Modifier.weight(1f)) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("주차 사진을 삭제할까요?") },
            text = { Text("삭제한 사진은 되돌릴 수 없어요. 주차 기록은 그대로 남아요.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("삭제", color = t.elapsedBurgundy) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", color = t.black) } },
            containerColor = t.white,
            titleContentColor = t.black,
            textContentColor = t.textSecondary,
        )
    }
}

@Composable
private fun PhotoAction(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = CarType.body.copy(fontWeight = FontWeight.Bold))
    }
}
