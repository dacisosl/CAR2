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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CarType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        color = LocalCarTokens.current.black,
        modifier = modifier.padding(top = 24.dp, bottom = 12.dp).semantics { heading() },
    )
}

/**
 * 사이드바 위치. 왼쪽(기본)은 넓은 패널에 층수 릴과 주차 영상, 오른쪽은 좁은 패널에 층수 릴만.
 * 사이드바는 끌어서 옮기지 않고 여기서만 바꾼다
 */
@Composable
fun SideChooser(current: DrawerSide, onSelect: (DrawerSide) -> Unit) {
    val t = LocalCarTokens.current
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(DrawerSide.Left, "왼쪽", "층수 릴 + 주차 영상"),
            Triple(DrawerSide.Right, "오른쪽", "층수 릴만"),
        ).forEach { (side, label, detail) ->
            val selected = current == side
            Column(
                Modifier
                    .weight(1f)
                    .clip(t.cardShape)
                    .border(if (selected) 2.dp else 1.dp, if (selected) t.primary else t.border, t.cardShape)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(side) }
                    .semantics(mergeDescendants = true) { contentDescription = "사이드바 $label, $detail" }
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SidePreview(side, Modifier.size(width = 56.dp, height = 92.dp))
                Spacer(Modifier.height(10.dp))
                Text(label, style = CarType.body.copy(fontWeight = FontWeight.Bold), color = t.black)
                Text(detail, style = CarType.label, color = t.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 휴대폰 화면 위 사이드바 모양 선 그림. 왼쪽은 넓은 패널(릴 띠 + 영상 자리), 오른쪽은 좁은 패널(릴 띠) */
@Composable
private fun SidePreview(side: DrawerSide, modifier: Modifier) {
    val t = LocalCarTokens.current
    Canvas(modifier) {
        val line = 2.dp.toPx()
        drawRoundRect(t.black, style = Stroke(line), cornerRadius = CornerRadius(9.dp.toPx()))
        val pad = 5.dp.toPx()
        val innerW = size.width - pad * 2
        val innerH = size.height - pad * 2
        val wide = side == DrawerSide.Left
        val panelW = innerW * if (wide) 0.9f else 0.5f
        val panelL = if (wide) pad else size.width - pad - panelW
        val panelT = pad + innerH * 0.18f
        val panelH = innerH * 0.64f
        drawRoundRect(t.primary.copy(alpha = 0.16f), Offset(panelL, panelT), Size(panelW, panelH), CornerRadius(3.dp.toPx()))
        val bandW = if (wide) panelW * 0.34f else panelW * 0.76f
        val bandL = if (wide) panelL + panelW * 0.06f else panelL + (panelW - bandW) / 2
        drawRoundRect(t.primary, Offset(bandL, panelT + panelH * 0.42f), Size(bandW, panelH * 0.16f), CornerRadius(2.dp.toPx()))
        if (wide) {
            val sceneL = bandL + bandW + panelW * 0.07f
            val sceneW = panelL + panelW * 0.94f - sceneL
            drawRoundRect(
                t.black.copy(alpha = 0.55f),
                Offset(sceneL, panelT + panelH * 0.32f),
                Size(sceneW, panelH * 0.36f),
                CornerRadius(2.dp.toPx()),
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }
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
