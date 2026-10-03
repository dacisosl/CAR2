package app.car.parking.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import app.car.parking.R
import app.car.parking.data.location.CurrentLocationState
import app.car.parking.data.storage.LocationSource
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.Floors
import app.car.parking.platform.permissions.AutoRecordState
import app.car.parking.platform.permissions.Readiness
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.components.rememberPhoto
import app.car.parking.ui.map.ParkingMap
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.primarySurface
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    record: ParkingRecordEntity?,
    readiness: AutoRecordState,
    location: CurrentLocationState,
    onOpenSettings: () -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenFloor: () -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    val t = LocalCarTokens.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(t.white).safeDrawingPadding()) {
        val side = if (maxWidth < 360.dp) 16.dp else 20.dp
        val topLimit = maxHeight * 0.62f
        Column(Modifier.fillMaxSize().padding(horizontal = side)) {
            Header(readiness, onOpenSettings, onOpenReadiness)
            // 큰 글자에서는 위쪽 정보만 스크롤하고 지도 공간은 남긴다
            Column(
                Modifier
                    .heightIn(max = topLimit)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))
                if (record != null) Text("주차한 지", style = CarType.secondary, color = t.textSecondary)
                Text(
                    text = record?.let { elapsedText(now - it.detectedAt) } ?: "아직 기록이 없어요",
                    style = if (record != null) CarType.elapsed else CarType.title,
                    color = t.black,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloorCard(record, onOpenFloor, Modifier.weight(1f))
                    VehicleCard(record?.photoPath, onOpenPhoto, Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                if (record != null) {
                    Text(
                        record.placeName ?: "주차 위치",
                        style = CarType.title,
                        color = t.black,
                    )
                    Text(
                        record.zoneMemo ?: locationSummary(record, now),
                        style = CarType.secondary,
                        color = t.textSecondary,
                    )
                } else {
                    Text(
                        "층수 카드를 눌러 직접 기록할 수 있어요",
                        style = CarType.secondary,
                        color = t.textSecondary,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            ParkingMap(
                record = record,
                location = location,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Header(readiness: AutoRecordState, onOpenSettings: () -> Unit, onOpenReadiness: () -> Unit) {
    val t = LocalCarTokens.current
    val active = readiness == AutoRecordState.Ready
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.car_logo),
            contentDescription = "CAR 주차기록",
            modifier = Modifier.width(104.dp),
            contentScale = ContentScale.FillWidth,
            colorFilter = t.logoFilter,
        )
        Spacer(Modifier.weight(1f))
        // 자동 기록 준비 상태: 활성은 검정 심볼 + 옅은 회색 채움, 비활성은 회색 심볼
        IconTarget(
            icon = R.drawable.ic_bluetooth,
            description = Readiness.description(readiness),
            onClick = onOpenReadiness,
            tint = if (active) t.onPrimary else t.inactive,
            background = if (active) t.primary else androidx.compose.ui.graphics.Color.Transparent,
            border = if (active) t.primaryHairline else null,
        )
        Spacer(Modifier.width(4.dp))
        IconTarget(icon = R.drawable.ic_settings, description = "설정", onClick = onOpenSettings, iconSize = 24.dp)
    }
}

@Composable
private fun FloorCard(record: ParkingRecordEntity?, onClick: () -> Unit, modifier: Modifier) {
    val t = LocalCarTokens.current
    val label = Floors.label(record?.floorLevel)
    val description = when {
        record == null -> "층수 기록 시작"
        label == null -> "주차 층수 미선택, 눌러서 선택"
        else -> "주차 층수 $label, 눌러서 수정"
    }
    Box(
        modifier
            .heightIn(min = 124.dp)
            .clip(t.featureCardShape)
            .primarySurface(t, t.featureCardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(16.dp),
    ) {
        Column {
            Text(if (record == null) "층수 기록" else "주차 층수", style = CarType.secondary, color = t.onPrimary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label ?: "—", style = CarType.homeFloor, color = t.onPrimary, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = t.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun VehicleCard(photoPath: String?, onOpenPhoto: (String) -> Unit, modifier: Modifier) {
    val t = LocalCarTokens.current
    val photo = rememberPhoto(photoPath, maxSidePx = 640)
    Box(
        modifier
            .heightIn(min = 124.dp)
            .clip(t.cardShape)
            .background(t.surface)
            .let { m ->
                if (photoPath != null) {
                    m.clickable(role = Role.Button) { onOpenPhoto(photoPath) }
                        .semantics { contentDescription = "주차 사진 보기" }
                } else m
            },
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Column(Modifier.padding(16.dp)) {
                Text("내 차량", style = CarType.secondary, color = t.black)
                Spacer(Modifier.height(10.dp))
                Image(
                    painterResource(t.vehicleRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = t.vehicleFilter,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            }
        }
    }
}

fun elapsedText(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0L) / 60_000L)
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return when {
        minutes < 1 -> "방금"
        days > 0 -> "${days}일 ${hours}시간"
        hours > 0 -> "${hours}시간 ${mins}분"
        else -> "${mins}분"
    }
}

/** 저장 위치의 출처와 정확도. 지하의 대체 좌표를 정확한 주차 면처럼 표시하지 않는다 */
private fun locationSummary(record: ParkingRecordEntity, now: Long): String {
    if (record.latitude == null) return "위치 없음 · 층수와 사진으로 기록됨"
    val accuracy = record.locationAccuracyMeters?.let { "오차 약 ${it.toInt()}m" } ?: "정확도 정보 없음"
    val source = when (record.locationSource) {
        LocationSource.AFTER_DISCONNECT -> "하차 직후 위치"
        LocationSource.MANUAL -> "기록 시 위치"
        else -> "저장 위치"
    }
    return "$source · $accuracy"
}
