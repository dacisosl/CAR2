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
import app.car.parking.ui.theme.CarTheme
import app.car.parking.ui.theme.CarTokens
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.MapColors
import app.car.parking.ui.theme.cardSurface
import app.car.parking.ui.theme.primarySurface

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CarType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        color = LocalCarTokens.current.black,
        modifier = modifier.padding(top = 24.dp, bottom = 12.dp).semantics { heading() },
    )
}

/** 미리보기 카드 5개(2열, 마지막 줄은 한 칸). 카드 전체를 눌러 선택하고 즉시 적용한다 */
@Composable
fun ThemeChooser(current: AppThemeId, onSelect: (AppThemeId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CarTokens.all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { theme ->
                    val selected = current == theme.id
                    Column(
                        Modifier
                            .weight(1f)
                            .selectable(selected = selected, role = Role.RadioButton) { onSelect(theme.id) }
                            .semantics(mergeDescendants = true) { contentDescription = "${theme.label} 테마, ${theme.description}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 각 카드는 해당 테마로 그린 축소 미리보기
                        CarTheme(theme.id) {
                            ThemePreview(selected)
                            Spacer(Modifier.height(8.dp))
                            Swatches()
                        }
                        Spacer(Modifier.height(8.dp))
                        RadioMark(selected)
                        Spacer(Modifier.height(6.dp))
                        Text(theme.label, style = CarType.body.copy(fontWeight = FontWeight.Bold), color = LocalCarTokens.current.black)
                        Text(
                            theme.description,
                            style = CarType.label,
                            color = LocalCarTokens.current.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // 홀수 개일 때 마지막 카드도 반 폭으로
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Swatches() {
    val t = LocalCarTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(t.primary, t.white, t.accent).forEach {
            Box(Modifier.size(16.dp).clip(CircleShape).background(it).border(1.dp, t.border, CircleShape))
        }
    }
}

@Composable
private fun ThemePreview(selected: Boolean) {
    val t = LocalCarTokens.current
    val frame = if (t.isSilver) RoundedCornerShape(6.dp) else RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(frame)
            .background(t.white)
            .border(if (selected) 2.dp else 1.dp, if (selected) (t.accentSilver ?: t.primary) else t.border, frame)
            .padding(10.dp),
    ) {
        Image(painterResource(R.drawable.car_logo), null, Modifier.width(52.dp), contentScale = ContentScale.FillWidth, colorFilter = t.logoFilter)
        Spacer(Modifier.height(6.dp))
        Text("주차한 지", style = CarType.label.copy(fontSize = 9.sp), color = t.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(t.featureCardShape)
                    .primarySurface(t, t.featureCardShape),
                contentAlignment = Alignment.Center,
            ) { Text("B2", color = t.onFeature, style = CarType.body.copy(fontWeight = FontWeight.ExtraBold)) }
            Box(
                Modifier.weight(1f).height(34.dp).clip(t.cardShape).cardSurface(t, t.cardShape).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(t.vehicleRes), null, contentScale = ContentScale.Fit, colorFilter = t.vehicleFilter) }
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
            .background(if (selected) t.primary else t.white)
            .border(1.5.dp, if (selected) t.primary else t.inactive, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = t.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun CheckRow(
    title: String,
    reason: String,
    done: Boolean,
    action: String?,
    onAction: (() -> Unit)?,
    required: Boolean = true,
    showActionWhenDone: Boolean = false,
) {
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
                .background(if (done) t.primary else t.white)
                .border(1.5.dp, if (done) t.primary else t.inactive, CircleShape),
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
        if ((!done || showActionWhenDone) && action != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(t.buttonShape)
                    .border(1.dp, t.accentSilver ?: t.primary, t.buttonShape)
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
                        .let { if (selected) it.primarySurface(t, t.cardShape) else it.background(t.surface) }
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(device) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            device.name ?: "이름 없는 기기",
                            style = CarType.body.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) t.onFeature else t.black,
                        )
                        if (device.likelyVehicle) {
                            Text("차량 오디오로 보이는 기기", style = CarType.label, color = if (selected) t.onFeature else t.textSecondary)
                        }
                    }
                    if (selected) Icon(painterResource(R.drawable.ic_check), "선택됨", tint = t.onFeature)
                }
            }
        }
    }
}
