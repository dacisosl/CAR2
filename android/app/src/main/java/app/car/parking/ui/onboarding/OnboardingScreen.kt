package app.car.parking.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.car.parking.R
import app.car.parking.data.bluetooth.BondedDevices
import app.car.parking.data.storage.AppSettings
import app.car.parking.platform.permissions.SystemChecks
import app.car.parking.ui.LaunchTestStatus
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.components.PrimaryButton
import app.car.parking.ui.settings.LaunchTestPanel
import app.car.parking.ui.settings.ReadinessChecklist
import app.car.parking.ui.settings.VehicleList
import app.car.parking.ui.settings.rememberSystemActions
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens

private const val WELCOME = 0
private const val VEHICLE = 1
private const val PERMISSIONS = 2
private const val TEST = 3

/** 첫 시작: 차량 선택 → 필요한 권한과 실행 조건 → 자동 화면 표시 테스트 */
@Composable
fun OnboardingScreen(
    settings: AppSettings,
    checks: SystemChecks,
    launchTest: LaunchTestStatus,
    onVehicle: (String, String?) -> Unit,
    onStartLaunchTest: () -> Unit,
    onRefresh: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(WELCOME) }
    BackHandler(enabled = step > WELCOME) { step-- }
    when (step) {
        WELCOME -> Welcome(onConnect = { step = VEHICLE }, onLater = onFinish)
        else -> StepScaffold(
            step = step,
            onBack = { step-- },
            nextLabel = when (step) {
                VEHICLE -> "다음"
                PERMISSIONS -> "다음"
                else -> "완료"
            },
            nextEnabled = when (step) {
                VEHICLE -> settings.registeredVehicleAddress != null
                PERMISSIONS -> checks.bluetoothPermission && checks.overlayAllowed
                else -> true
            },
            onNext = { if (step == TEST) onFinish() else step++ },
            onSkip = onFinish,
        ) {
            val context = LocalContext.current
            val actions = rememberSystemActions(onRefresh)
            when (step) {
                VEHICLE -> {
                    StepTitle("내 차량 선택", "이미 페어링한 기기 중 차량을 고르세요. 이어폰·시계 연결에는 반응하지 않아요.")
                    VehicleList(
                        devices = remember(checks.bluetoothPermission) { BondedDevices.list(context) },
                        selectedAddress = settings.registeredVehicleAddress,
                        hasPermission = checks.bluetoothPermission,
                        onRequestPermission = actions::requestBluetooth,
                        onOpenBluetoothSettings = actions::openBluetoothSettings,
                        onSelect = { onVehicle(it.address, it.name) },
                    )
                }
                PERMISSIONS -> {
                    StepTitle("자동 기록 준비", "이 휴대폰에서 아직 필요한 항목이에요. 설정에서 돌아오면 상태를 다시 확인해요.")
                    ReadinessChecklist(settings, checks, actions)
                }
                TEST -> {
                    StepTitle("자동 표시 확인", "차에서 내렸을 때처럼 알림 없이 앱이 떠오르는지 지금 확인해요.")
                    LaunchTestPanel(
                        status = launchTest,
                        passedInfo = null,
                        overlayAllowed = checks.overlayAllowed,
                        onStart = onStartLaunchTest,
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome(onConnect: () -> Unit, onLater: () -> Unit) {
    val t = LocalCarTokens.current
    Column(
        Modifier
            .fillMaxSize()
            .background(t.white)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Image(painterResource(R.drawable.car_logo), "CAR 주차기록", Modifier.width(168.dp), contentScale = ContentScale.FillWidth, colorFilter = t.logoFilter)
        Spacer(Modifier.height(40.dp))
        Image(
            painterResource(t.vehicleRes),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.72f),
            contentScale = ContentScale.FillWidth,
            colorFilter = t.vehicleFilter,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            "주차 위치를\n쉽게 기억하세요",
            style = CarType.title.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
            color = t.black,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text("차에서 내리면\n주차 기록을 도와드려요", style = CarType.body, color = t.black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("차량 선택 · 권한 확인 · 연결 테스트", style = CarType.secondary, color = t.textSecondary)
        Spacer(Modifier.height(48.dp))
        PrimaryButton("내 차량 연결하기", onConnect, Modifier.fillMaxWidth().heightIn(min = 52.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onLater),
            contentAlignment = Alignment.Center,
        ) { Text("나중에 설정", style = CarType.body.copy(fontWeight = FontWeight.Bold), color = t.black) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepScaffold(
    step: Int,
    onBack: () -> Unit,
    nextLabel: String,
    nextEnabled: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable () -> Unit,
) {
    val t = LocalCarTokens.current
    Column(Modifier.fillMaxSize().background(t.white).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTarget(R.drawable.ic_arrow_back, "이전", onBack)
            Text("$step / 3", style = CarType.secondary, color = t.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("나중에", style = CarType.secondary, color = t.textSecondary) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
        PrimaryButton(
            nextLabel,
            onNext,
            Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp),
            enabled = nextEnabled,
        )
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    val t = LocalCarTokens.current
    Spacer(Modifier.height(12.dp))
    Text(title, style = CarType.title, color = t.black, modifier = Modifier.semantics { heading() })
    Text(body, style = CarType.secondary, color = t.textSecondary)
    Spacer(Modifier.height(12.dp))
}
