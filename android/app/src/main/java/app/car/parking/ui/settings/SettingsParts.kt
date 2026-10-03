package app.car.parking.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.car.parking.R
import app.car.parking.data.bluetooth.PairedDevice
import app.car.parking.data.storage.AppThemeId
import app.car.parking.data.storage.DrawerSide
import app.car.parking.ui.LaunchTestStatus
import app.car.parking.ui.theme.CarTheme
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.MapColors

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CarType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        color = LocalCarTokens.current.black,
        modifier = modifier.padding(top = 24.dp, bottom = 12.dp).semantics { heading() },
    )
}

/** 미리보기 카드 두 개. 카드 전체를 눌러 선택하고 즉시 적용한다 */
@Composable
fun ThemeChooser(current: AppThemeId, onSelect: (AppThemeId) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(AppThemeId.Classic, "클래식", "블랙 · 화이트"),
            Triple(AppThemeId.Steel, "스틸 포인트", "얇은 실버 디테일"),
        ).forEach { (id, label, desc) ->
            val selected = current == id
            Column(
                Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(id) }
                    .semantics(mergeDescendants = true) { contentDescription = "$label 테마, $desc" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 각 카드는 해당 테마로 그린 축소 미리보기
                CarTheme(id) { ThemePreview(selected) }
                Spacer(Modifier.height(10.dp))
                RadioMark(selected)
                Spacer(Modifier.height(6.dp))
                Text(label, style = CarType.body.copy(fontWeight = FontWeight.Bold), color = LocalCarTokens.current.black)
                Text(desc, style = CarType.label, color = LocalCarTokens.current.textSecondary)
            }
        }
    }
}

@Composable
private fun ThemePreview(selected: Boolean) {
    val t = LocalCarTokens.current
    val frame = if (t.isSteel) RoundedCornerShape(6.dp) else RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(frame)
            .background(t.white)
            .border(if (selected) 2.dp else 1.dp, if (selected) (t.accentSilver ?: t.black) else t.border, frame)
            .padding(10.dp),
    ) {
        Image(painterResource(R.drawable.car_logo), null, Modifier.width(52.dp), contentScale = ContentScale.FillWidth)
        Spacer(Modifier.height(6.dp))
        Text("주차한 지", style = CarType.label.copy(fontSize = 9.sp), color = t.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(t.featureCardShape)
                    .background(t.black)
                    .let { m -> t.primaryBorder?.let { m.border(it, t.featureCardShape) } ?: m },
                contentAlignment = Alignment.Center,
            ) { Text("B2", color = t.onPrimary, style = CarType.body.copy(fontWeight = FontWeight.ExtraBold)) }
            Box(
                Modifier.weight(1f).height(34.dp).clip(t.cardShape).background(t.surface).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(t.vehicleRes), null, contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(MapColors.background))
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    val t = LocalCarTokens.current
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) t.black else t.white)
            .border(1.5.dp, if (selected) t.black else t.inactive, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = t.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun SideChooser(side: DrawerSide, onSelect: (DrawerSide) -> Unit) {
    val t = LocalCarTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(t.buttonShape)
            .background(t.surface)
            .padding(4.dp),
    ) {
        listOf(DrawerSide.Left to "왼쪽", DrawerSide.Right to "오른쪽").forEach { (value, label) ->
            val selected = side == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(t.buttonShape)
                    .background(if (selected) t.black else t.surface)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(value) }
                    .semantics { contentDescription = "사이드바 $label" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = CarType.body.copy(fontWeight = FontWeight.Bold), color = if (selected) t.onPrimary else t.black)
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val t = LocalCarTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = CarType.body.copy(fontWeight = FontWeight.Bold), color = t.black)
            Text(subtitle, style = CarType.secondary, color = t.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = t.black,
                checkedThumbColor = t.white,
                uncheckedTrackColor = t.border,
                uncheckedThumbColor = t.white,
                uncheckedBorderColor = t.border,
            ),
        )
    }
}

/** 실제 상태 + 이유 + 이동 버튼 */
@Composable
fun CheckRow(title: String, reason: String, done: Boolean, action: String?, onAction: (() -> Unit)?, required: Boolean = true) {
    val t = LocalCarTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (done) t.black else t.white)
                .border(1.5.dp, if (done) t.black else t.inactive, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.ic_check), null, tint = t.onPrimary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title + if (!required) " (선택)" else "",
                style = CarType.body.copy(fontWeight = FontWeight.Bold),
                color = t.black,
            )
            Text(if (done) "완료 · $reason" else reason, style = CarType.secondary, color = t.textSecondary)
        }
        if (!done && action != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(t.buttonShape)
                    .border(1.dp, t.accentSilver ?: t.black, t.buttonShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, style = CarType.secondary.copy(fontWeight = FontWeight.Bold), color = t.black) }
        }
    }
}

@Composable
fun VehicleList(
    devices: List<PairedDevice>,
    selectedAddress: String?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onSelect: (PairedDevice) -> Unit,
) {
    val t = LocalCarTokens.current
    when {
        !hasPermission -> CheckRow(
            "근처 기기 권한",
            "페어링된 차량을 찾고 연결 해제를 알아차리는 데 필요해요",
            done = false,
            action = "허용",
            onAction = onRequestPermission,
        )
        devices.isEmpty() -> Column {
            Text(
                "페어링된 기기가 없어요. 휴대폰 Bluetooth 설정에서 차량을 먼저 연결하세요.",
                style = CarType.secondary,
                color = t.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            CheckRow("Bluetooth 설정", "차량과 페어링한 뒤 돌아오세요", false, "열기", onOpenBluetoothSettings)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { device ->
                val selected = device.address.equals(selectedAddress, ignoreCase = true)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(t.cardShape)
                        .background(if (selected) t.black else t.surface)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(device) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            device.name ?: "이름 없는 기기",
                            style = CarType.body.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) t.onPrimary else t.black,
                        )
                        if (device.likelyVehicle) {
                            Text("차량 오디오로 보이는 기기", style = CarType.label, color = if (selected) t.onPrimary else t.textSecondary)
                        }
                    }
                    if (selected) Icon(painterResource(R.drawable.ic_check), "선택됨", tint = t.onPrimary)
                }
            }
        }
    }
}

@Composable
fun LaunchTestPanel(status: LaunchTestStatus, passedInfo: String?, overlayAllowed: Boolean, onStart: () -> Unit) {
    val t = LocalCarTokens.current
    val message = when (status) {
        LaunchTestStatus.Waiting -> "지금 홈 화면으로 나가세요. 8초 뒤 앱이 자동으로 나타나야 해요."
        LaunchTestStatus.Passed -> "통과 · 알림을 누르지 않고 앱이 자동으로 표시됐어요."
        LaunchTestStatus.NotInBackground -> "앱이 화면에 떠 있어서 확인하지 못했어요. 시작 후 홈 화면으로 나가세요."
        LaunchTestStatus.Failed -> "앱이 자동으로 표시되지 않았어요. ‘다른 앱 위에 표시’와 제조사 실행 제한을 확인하세요."
        LaunchTestStatus.Idle -> passedInfo?.let { "통과 기록 · $it" }
            ?: "시작을 누른 뒤 홈 화면으로 나가면, 차에서 내렸을 때와 같은 방식으로 앱이 떠오르는지 확인해요."
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(t.cardShape)
            .background(t.surface)
            .padding(16.dp),
    ) {
        Text("자동 표시 테스트", style = CarType.body.copy(fontWeight = FontWeight.Bold), color = t.black)
        Spacer(Modifier.height(4.dp))
        Text(message, style = CarType.secondary, color = t.textSecondary)
        Text(
            "실제 차량 Bluetooth 해제 감지는 차에서 따로 확인해야 해요.",
            style = CarType.label,
            color = t.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(t.buttonShape)
                .background(if (overlayAllowed) t.black else t.border)
                .clickable(enabled = overlayAllowed && status != LaunchTestStatus.Waiting, role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    !overlayAllowed -> "‘다른 앱 위에 표시’를 먼저 허용하세요"
                    status == LaunchTestStatus.Waiting -> "대기 중"
                    else -> "테스트 시작"
                },
                style = CarType.body.copy(fontWeight = FontWeight.Bold),
                color = if (overlayAllowed) t.onPrimary else t.inactive,
                textAlign = TextAlign.Center,
            )
        }
    }
}
