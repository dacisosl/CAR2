package app.car.parking.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.car.parking.BuildConfig
import app.car.parking.R
import app.car.parking.data.bluetooth.BondedDevices
import app.car.parking.data.storage.AppSettings
import app.car.parking.data.storage.AppThemeId
import app.car.parking.data.storage.DrawerSide
import app.car.parking.platform.permissions.AutoRecordState
import app.car.parking.platform.permissions.Readiness
import app.car.parking.platform.permissions.SystemChecks
import app.car.parking.platform.statusbar.StatusBarNotifier
import app.car.parking.ui.LaunchTestStatus
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    checks: SystemChecks,
    readiness: AutoRecordState,
    launchTest: LaunchTestStatus,
    scrollToReadiness: Boolean,
    onBack: () -> Unit,
    onTheme: (AppThemeId) -> Unit,
    onSide: (DrawerSide) -> Unit,
    onAutoRecord: (Boolean) -> Unit,
    onStatusBar: (Boolean) -> Unit,
    onVehicle: (String, String?) -> Unit,
    onStartLaunchTest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val t = LocalCarTokens.current
    val context = LocalContext.current
    val actions = rememberSystemActions(onRefresh)
    // 테마를 바꿔도 같은 화면과 스크롤 위치를 유지한다
    val scroll = rememberScrollState()
    var showVehicles by rememberSaveable { mutableStateOf(false) }
    var readinessY by remember { mutableStateOf(0) }

    LaunchedEffect(scrollToReadiness, readinessY) {
        if (scrollToReadiness && readinessY > 0) scroll.animateScrollTo(readinessY)
    }

    Column(Modifier.fillMaxSize().background(t.white).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconTarget(R.drawable.ic_arrow_back, "뒤로", onBack, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                "설정",
                style = CarType.title,
                color = t.black,
                modifier = Modifier.align(Alignment.Center).semantics { heading() },
            )
        }
        HorizontalDivider(color = t.border)
        Column(Modifier.verticalScroll(scroll).padding(horizontal = 20.dp)) {
            SectionTitle("디자인 테마")
            ThemeChooser(settings.appTheme, onTheme)
            Text(
                "선택하면 바로 적용돼요",
                style = CarType.secondary,
                color = t.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            SectionTitle("사이드바 위치")
            SideChooser(settings.drawerSide, onSide)
            Text(
                "층수 패널 위쪽 손잡이를 길게 눌러 끌어도 바꿀 수 있어요",
                style = CarType.secondary,
                color = t.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle("자동 기록")
            SwitchRow(
                "자동 기록",
                "차에서 내리면 자동으로 기록해요",
                settings.autoRecordRequested,
                onAutoRecord,
            )
            CheckRow(
                "등록 차량",
                settings.registeredVehicleName ?: settings.registeredVehicleAddress?.let { "이름 없는 기기" } ?: "등록된 차량이 없어요",
                done = settings.registeredVehicleAddress != null,
                action = "선택",
                onAction = { showVehicles = true },
            )
            if (settings.registeredVehicleAddress != null && !showVehicles) {
                CheckRow("다른 차량으로 변경", "페어링된 기기 목록에서 다시 선택해요", false, "변경", { showVehicles = true }, required = false)
            }
            if (showVehicles) {
                VehicleList(
                    devices = remember(checks.bluetoothPermission) { BondedDevices.list(context) },
                    selectedAddress = settings.registeredVehicleAddress,
                    hasPermission = checks.bluetoothPermission,
                    onRequestPermission = actions::requestBluetooth,
                    onOpenBluetoothSettings = actions::openBluetoothSettings,
                    onSelect = {
                        onVehicle(it.address, it.name)
                        showVehicles = false
                    },
                )
            }

            SectionTitle("상태바 층 표시")
            SwitchRow(
                "상태바에 층수 표시",
                "저장한 주차 층수를 상태바 아이콘과 알림으로 보여줘요",
                settings.statusBarEnabled,
                onStatusBar,
            )
            if (settings.statusBarEnabled && !checks.notificationsAllowed) {
                CheckRow("알림 권한", "상태바에 층수를 표시하려면 필요해요", false, "허용", actions::requestNotifications)
            }
            if (settings.statusBarEnabled && checks.notificationsAllowed) {
                val live = StatusBarNotifier.liveUpdatesState(context)
                Text(
                    when (live) {
                        StatusBarNotifier.LiveUpdatesState.Ready -> "Android 16 실시간 업데이트로 상태바 칩과 잠금 화면에도 표시돼요"
                        StatusBarNotifier.LiveUpdatesState.Disabled -> "앱 알림 설정에서 ‘실시간 업데이트’를 켜면 상태바 칩으로 표시돼요"
                        StatusBarNotifier.LiveUpdatesState.Unsupported -> "이 Android 버전에서는 일반 상시 알림으로 표시돼요"
                    },
                    style = CarType.secondary,
                    color = t.textSecondary,
                )
                if (live == StatusBarNotifier.LiveUpdatesState.Disabled) {
                    CheckRow("실시간 업데이트", "앱 알림 설정에서 켤 수 있어요", false, "열기", { StatusBarNotifier.openNotificationSettings(context) }, required = false)
                }
            }

            Column(
                Modifier.onGloballyPositioned { readinessY = it.positionInParent().y.toInt() },
            ) {
                SectionTitle("자동 기록 준비 상태")
                Text(Readiness.description(readiness), style = CarType.secondary, color = t.textSecondary)
            }
            ReadinessChecklist(settings, checks, actions)
            Spacer(Modifier.height(12.dp))
            LaunchTestPanel(
                status = launchTest,
                passedInfo = settings.autoLaunchTestPassedAt.takeIf { it > 0 }?.let {
                    "${formatDate(it)} · ${settings.autoLaunchTestDevice ?: Build.MODEL}"
                },
                overlayAllowed = checks.overlayAllowed,
                onStart = onStartLaunchTest,
            )
            Text(
                "휴대폰 설정에서 앱을 ‘강제 중지’하면 다음에 앱을 직접 열기 전까지 자동 기록이 동작하지 않아요. " +
                    "제조사 절전 정책에 따라 백그라운드 감지가 제한될 수 있어요.",
                style = CarType.label,
                color = t.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )

            SectionTitle("층수 추천")
            Text(
                if (checks.hasBarometer) {
                    "기압 센서가 있어요. 같은 주차장에서 최근에 확인한 층이 있을 때만 층수를 추천하고, 근거가 부족하면 직접 선택해요."
                } else {
                    "이 기기는 기압 센서가 없어 층수 추천을 지원하지 않아요. 릴에서 직접 선택해요."
                },
                style = CarType.secondary,
                color = t.textSecondary,
            )

            SectionTitle("앱 정보")
            Text("주차기록 (가칭 CAR) ${BuildConfig.VERSION_NAME}", style = CarType.secondary, color = t.textSecondary)
            Text(
                if (BuildConfig.NAVER_MAP_KEY_ID.isBlank()) "지도: 미연결 (지도 키 없음)" else "지도: 네이버 지도",
                style = CarType.secondary,
                color = t.textSecondary,
            )
            Spacer(Modifier.height(32.dp))
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
    CheckRow("위치", "주차 위치를 지도에 표시해요", checks.anyLocation, "허용", actions::requestLocation, required = false)
    if (Build.VERSION.SDK_INT >= 33) {
        CheckRow("알림", "상태바 층 표시와 자동 표시가 막혔을 때 안내에 사용해요", checks.notificationsAllowed, "허용", actions::requestNotifications, required = false)
    }
    CheckRow("배터리 사용 제한 없음", "일부 기기에서 백그라운드 감지가 멈추지 않게 해요", checks.batteryUnrestricted, "설정", actions::openBatterySettings, required = false)
}

private fun formatDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(ms))
