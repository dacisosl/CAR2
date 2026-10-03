package app.car.parking.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import app.car.parking.R
import app.car.parking.data.bluetooth.BondedDevices
import app.car.parking.data.location.Fix
import app.car.parking.data.storage.AppSettings
import app.car.parking.platform.permissions.AutoRecordState
import app.car.parking.platform.permissions.Readiness
import app.car.parking.platform.permissions.SystemChecks
import app.car.parking.platform.statusbar.StatusBarNotifier
import app.car.parking.ui.SettingsDraft
import app.car.parking.ui.UpdateState
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.theme.CarTheme
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.primarySurface
import kotlinx.coroutines.launch

/**
 * 설정: 자동설정 관리 · 차량 관리 · 위치 관리 · 디자인 설정.
 * 차량·집 위치·디자인은 초안으로 고치고 우측 상단 ‘저장’으로 확정한다.
 * 권한·시스템 설정은 휴대폰 설정에서 바로 바뀌므로 실제 상태를 다시 읽어 보여준다.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    checks: SystemChecks,
    readiness: AutoRecordState,
    scrollToReadiness: Boolean,
    onBack: () -> Unit,
    onSave: (SettingsDraft) -> Unit,
    locateHere: suspend () -> Fix?,
    onRefresh: () -> Unit,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val saved = remember(settings) { SettingsDraft.from(settings) }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(saved) }
    val dirty = draft != saved
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (dirty) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    // 디자인 미리보기도 저장 전 초안 테마로 보여준다
    CarTheme(draft.theme) {
        val t = LocalCarTokens.current
        val context = LocalContext.current
        val actions = rememberSystemActions(onRefresh)
        val scope = rememberCoroutineScope()
        val scroll = rememberScrollState()
        var showVehicles by rememberSaveable { mutableStateOf(false) }
        var themesOpen by rememberSaveable { mutableStateOf(false) }
        var locatingHome by remember { mutableStateOf(false) }
        var homeMessage by remember { mutableStateOf<String?>(null) }
        var readinessY by remember { mutableStateOf(0) }

        LaunchedEffect(scrollToReadiness, readinessY) {
            if (scrollToReadiness && readinessY > 0) scroll.animateScrollTo(readinessY)
        }

        Column(Modifier.fillMaxSize().background(t.white).safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconTarget(R.drawable.ic_arrow_back, "뒤로", leave, modifier = Modifier.align(Alignment.CenterStart))
                Text(
                    "설정",
                    style = CarType.title,
                    color = t.black,
                    modifier = Modifier.align(Alignment.Center).semantics { heading() },
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .heightIn(min = 48.dp)
                        .clip(t.buttonShape)
                        .primarySurface(t, t.buttonShape, enabled = dirty)
                        .clickable(enabled = dirty, role = Role.Button) { onSave(draft) }
                        .semantics { contentDescription = if (dirty) "설정 저장" else "저장, 바뀐 내용 없음" }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "저장",
                        style = CarType.secondary.copy(fontWeight = FontWeight.Bold),
                        color = if (dirty) t.onFeature else t.inactive,
                    )
                }
            }
            HorizontalDivider(color = t.border)
            Column(Modifier.verticalScroll(scroll).padding(horizontal = 20.dp)) {
                // ── 자동설정 관리 ──
                Column(Modifier.onGloballyPositioned { readinessY = it.positionInParent().y.toInt() }) {
                    SectionTitle("자동설정 관리")
                    Text(Readiness.description(readiness), style = CarType.secondary, color = t.textSecondary)
                }
                ReadinessChecklist(settings, checks, actions)
                CheckRow(
                    "앱 사용 안 할 때 권한 유지",
                    "오래 열지 않아도 Bluetooth 권한이 회수되지 않게 해요",
                    checks.unusedAppExempt,
                    "설정",
                    {
                        runCatching {
                            context.startActivity(
                                IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { actions.openAppDetails() }
                    },
                    required = false,
                )
                checks.liveUpdatesAllowed?.let { allowed ->
                    CheckRow(
                        "실시간 업데이트",
                        "상태바 층수를 칩·잠금 화면에도 표시해요 (Android 16)",
                        allowed,
                        "설정",
                        { StatusBarNotifier.openNotificationSettings(context) },
                        required = false,
                    )
                }
                Text(
                    "휴대폰 설정에서 앱을 ‘강제 중지’하면 다음에 앱을 직접 열기 전까지 자동 기록이 동작하지 않아요. " +
                        "제조사 절전 정책에 따라 백그라운드 감지가 제한될 수 있어요.",
                    style = CarType.label,
                    color = t.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // ── 차량 관리 ──
                SectionTitle("차량 관리")
                CheckRow(
                    "등록 차량",
                    draft.vehicleName ?: draft.vehicleAddress?.let { "이름 없는 기기" } ?: "등록된 차량이 없어요",
                    done = draft.vehicleAddress != null,
                    action = if (draft.vehicleAddress == null) "선택" else "변경",
                    onAction = { showVehicles = !showVehicles },
                )
                if (draft.vehicleAddress != null && !showVehicles) {
                    Text(
                        "등록한 차량의 연결이 끊길 때만 주차로 기록해요. 이어폰·시계에는 반응하지 않아요.",
                        style = CarType.label,
                        color = t.textSecondary,
                    )
                }
                if (showVehicles) {
                    VehicleList(
                        devices = remember(checks.bluetoothPermission) { BondedDevices.list(context) },
                        selectedAddress = draft.vehicleAddress,
                        hasPermission = checks.bluetoothPermission,
                        onRequestPermission = actions::requestBluetooth,
                        onOpenBluetoothSettings = actions::openBluetoothSettings,
                        onSelect = {
                            draft = draft.copy(vehicleAddress = it.address, vehicleName = it.name)
                            showVehicles = false
                        },
                    )
                }

                // ── 위치 관리 ──
                SectionTitle("위치 관리")
                CheckRow(
                    "위치 권한",
                    if (checks.fineLocation) "주차 위치를 지도에 표시해요" else "정확한 위치를 허용하면 주차 위치가 더 정확해요",
                    checks.fineLocation,
                    "허용",
                    actions::requestLocation,
                )
                CheckRow("위치 서비스", "휴대폰의 위치(GPS)가 켜져 있어야 해요", checks.locationServiceOn, "열기", actions::openLocationSettings)
                CheckRow(
                    "집 위치",
                    homeMessage ?: if (draft.homeLatitude != null) {
                        "등록됨 · 집 근처 주차는 상태바 표시가 기본으로 켜져요"
                    } else {
                        "등록하면 집 근처 주차만 상태바 표시가 기본으로 켜져요"
                    },
                    done = draft.homeLatitude != null,
                    action = when {
                        locatingHome -> "확인 중"
                        draft.homeLatitude != null -> "다시 등록"
                        else -> "현재 위치로"
                    },
                    onAction = {
                        if (!checks.anyLocation) {
                            actions.requestLocation()
                        } else if (!locatingHome) {
                            locatingHome = true
                            homeMessage = "현재 위치를 확인하고 있어요"
                            scope.launch {
                                val fix = locateHere()
                                locatingHome = false
                                if (fix != null) {
                                    draft = draft.copy(homeLatitude = fix.latitude, homeLongitude = fix.longitude)
                                    homeMessage = "현재 위치를 집으로 지정했어요. 저장을 눌러 확정하세요"
                                } else {
                                    homeMessage = "현재 위치를 찾지 못했어요"
                                }
                            }
                        }
                    },
                    showActionWhenDone = true,
                )
                if (draft.homeLatitude != null) {
                    Text(
                        "집 위치 삭제",
                        style = CarType.secondary.copy(fontWeight = FontWeight.Bold),
                        color = t.textSecondary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) {
                                draft = draft.copy(homeLatitude = null, homeLongitude = null)
                                homeMessage = null
                            }
                            .padding(vertical = 14.dp),
                    )
                }

                // ── 디자인 설정 (기본 접힘) ──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { themesOpen = !themesOpen }
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (themesOpen) "펼침" else "접힘"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "디자인 설정",
                            style = CarType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = t.black,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(t.label + " · " + t.description, style = CarType.secondary, color = t.textSecondary)
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = t.black,
                        modifier = Modifier.size(24.dp).rotate(if (themesOpen) 270f else 90f),
                    )
                }
                AnimatedVisibility(themesOpen) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        ThemeChooser(draft.theme) { draft = draft.copy(theme = it) }
                        Text(
                            "저장을 누르면 앱 전체에 적용돼요",
                            style = CarType.secondary,
                            color = t.textSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                UpdateRow(update, onCheckUpdate, onInstallUpdate)
            }
        }

        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text("저장하지 않은 변경") },
                text = { Text("바꾼 설정을 저장할까요?") },
                confirmButton = {
                    TextButton(onClick = { confirmLeave = false; onSave(draft); onBack() }) { Text("저장", color = t.black) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmLeave = false; onBack() }) { Text("저장 안 함", color = t.textSecondary) }
                },
            )
        }
    }
}

@Composable
fun ReadinessChecklist(settings: AppSettings, checks: SystemChecks, actions: SystemActions) {
    if (!checks.bluetoothSupported) {
        CheckRow("Bluetooth", "이 기기는 Bluetooth를 지원하지 않아 자동 기록을 쓸 수 없어요", false, null, null)
        return
    }
    if (Build.VERSION.SDK_INT >= 31) {
        CheckRow("근처 기기", "등록 차량의 연결과 해제를 알아차려요", checks.bluetoothPermission, "허용", actions::requestBluetooth)
    }
    CheckRow("Bluetooth 켜짐", "Bluetooth가 꺼져 있으면 감지할 수 없어요", checks.bluetoothEnabled, "열기", actions::openBluetoothSettings)
    CheckRow("다른 앱 위에 표시", "차에서 내리면 알림을 누르지 않아도 앱이 바로 떠요", checks.overlayAllowed, "설정", actions::openOverlaySettings)
    if (Build.VERSION.SDK_INT >= 33) {
        CheckRow("알림", "상태바 층수 표시에 필요해요", checks.notificationsAllowed, "허용", actions::requestNotifications, required = false)
    }
    CheckRow("배터리 사용 제한 없음", "일부 기기에서 백그라운드 감지가 멈추지 않게 해요", checks.batteryUnrestricted, "설정", actions::openBatterySettings, required = false)
}

private val DraftSaver = androidx.compose.runtime.saveable.listSaver<SettingsDraft, Any?>(
    save = { listOf(it.theme.key, it.vehicleAddress, it.vehicleName, it.homeLatitude, it.homeLongitude) },
    restore = {
        SettingsDraft(
            theme = app.car.parking.data.storage.AppThemeId.from(it[0] as String?),
            vehicleAddress = it[1] as String?,
            vehicleName = it[2] as String?,
            homeLatitude = it[3] as Double?,
            homeLongitude = it[4] as Double?,
        )
    },
)
