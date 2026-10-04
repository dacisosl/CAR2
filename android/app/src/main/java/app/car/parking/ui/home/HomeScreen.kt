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
import androidx.compose.foundation.shape.CircleShape
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
import app.car.parking.ui.theme.ElapsedTone
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.LocationSaveStatus
import app.car.parking.ui.VehicleStatus
import app.car.parking.ui.theme.cardSurface
import app.car.parking.ui.theme.primarySurface
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun HomeScreen(
    record: ParkingRecordEntity?,
    readiness: AutoRecordState,
    vehicleStatus: VehicleStatus,
    location: CurrentLocationState,
    onOpenSettings: () -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenFloor: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onTakePhoto: () -> Unit,
    locationSave: LocationSaveStatus,
    onSaveLocation: () -> Unit,
    /** 자동 진입 패널이 먼저 그려지도록 무거운 지도 생성을 잠깐 미룬다 */
    deferMap: Boolean = false,
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
            Header(readiness, vehicleStatus, onOpenSettings, onOpenReadiness)
            VehicleStatusLine(vehicleStatus, now, onOpenFloor)
            // 큰 글자에서는 위쪽 정보만 스크롤하고 지도 공간은 남긴다
            Column(
                Modifier
                    .heightIn(max = topLimit)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val driving = vehicleStatus as? VehicleStatus.Driving
                    Column(Modifier.weight(1f)) {
                        if (driving != null) {
                            // 차량이 연결되면 주차 시간 대신 운전 시간을 0부터 센다
                            Text("운전 중", style = CarType.secondary, color = t.textSecondary)
                            Text(
                                text = if (driving.sinceMs > 0L) elapsedText(now - driving.sinceMs) else "방금",
                                style = CarType.elapsed,
                                color = t.black,
                                modifier = Modifier.semantics { heading() },
                            )
                        } else {
                            if (record != null) Text("주차한 지", style = CarType.secondary, color = t.textSecondary)
                            Text(
                                text = record?.let { elapsedText(now - it.detectedAt) } ?: "아직 기록이 없어요",
                                style = if (record != null) CarType.elapsed else CarType.title,
                                // 2시간부터 진한 초록, 3시간부터 버건디(UHD는 어두운 바탕용 밝은 색)
                                color = record?.let { ElapsedTone.color(now - it.detectedAt, t) } ?: t.black,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                    // UHD: 0~3시간 계기판(운전 중에는 숨김)
                    if (t.dial && record != null && driving == null) ElapsedDial(now - record.detectedAt)
                }
                Spacer(Modifier.height(16.dp))
                // 두 카드는 항상 같은 높이: 더 큰 쪽 내용 높이(최소 124dp)에 맞춘다. 사진 유무·로딩·글자 크기와 무관
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FloorCard(record, onOpenFloor, Modifier.weight(1f).fillMaxHeight())
                    VehicleCard(record != null, record?.photoPath, onOpenPhoto, onTakePhoto, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.height(20.dp))
                if (record != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                record.placeName ?: "주차 위치",
                                style = CarType.title,
                                color = t.black,
                            )
                            Text(
                                when (locationSave) {
                                    LocationSaveStatus.Saving -> "현재 위치를 확인하고 있어요"
                                    LocationSaveStatus.Saved -> "현재 위치를 주차 위치로 저장했어요"
                                    LocationSaveStatus.Failed -> "현재 위치를 찾지 못했어요. 잠시 후 다시 눌러 주세요"
                                    LocationSaveStatus.Idle -> record.zoneMemo ?: locationSummary(record)
                                },
                                style = CarType.secondary,
                                color = t.textSecondary,
                            )
                        }
                        // 위치 저장: 지금 위치를 이 기록의 주차 위치로 저장
                        IconTarget(
                            icon = R.drawable.ic_add_location,
                            description = "현재 위치를 주차 위치로 저장",
                            onClick = onSaveLocation,
                            enabled = locationSave != LocationSaveStatus.Saving,
                            tint = t.onPrimary,
                            background = t.primary,
                            border = t.primaryHairline,
                            iconSize = 22.dp,
                        )
                    }
                } else {
                    Text(
                        "층수 카드를 눌러 직접 기록할 수 있어요",
                        style = CarType.secondary,
                        color = t.textSecondary,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            val exited = vehicleStatus as? VehicleStatus.Exited
            ParkingMap(
                record = record,
                location = location,
                pendingLat = exited?.latitude,
                pendingLng = exited?.longitude,
                deferHost = deferMap,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Header(
    readiness: AutoRecordState,
    vehicleStatus: VehicleStatus,
    onOpenSettings: () -> Unit,
    onOpenReadiness: () -> Unit,
) {
    val t = LocalCarTokens.current
    val ready = readiness == AutoRecordState.Ready
    val driving = vehicleStatus is VehicleStatus.Driving
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
        // 명암·채움으로 상태 표현: 차량 연결(이동 중) = 주색 채움, 준비됨 = 진한 심볼 + 테두리, 꺼짐 = 회색 심볼
        IconTarget(
            icon = R.drawable.ic_bluetooth,
            description = if (driving) "차량 연결됨, 이동 중" else Readiness.description(readiness),
            onClick = onOpenReadiness,
            tint = when {
                driving -> t.onPrimary
                ready -> t.black
                else -> t.inactive
            },
            background = if (driving) t.primary else androidx.compose.ui.graphics.Color.Transparent,
            border = if (driving) t.primaryHairline else if (ready) t.border else null,
        )
        Spacer(Modifier.width(4.dp))
        IconTarget(icon = R.drawable.ic_settings, description = "설정", onClick = onOpenSettings, iconSize = 24.dp)
    }
}

/**
 * 차량 상태 한 줄. 연결돼 있으면 ‘이동 중’, 해제를 감지해 기록을 기다리는 후보가 있으면 ‘하차 감지’.
 * 보여 줄 것이 없으면 아무 줄도 두지 않는다(홈에 상시 안내 문구를 두지 않는 규칙).
 */
@Composable
private fun VehicleStatusLine(status: VehicleStatus, now: Long, onOpenFloor: () -> Unit) {
    val t = LocalCarTokens.current
    val (text, color, action) = when (status) {
        is VehicleStatus.Driving -> Triple(
            "차량 연결됨 · 이동 중" + sinceText(now - status.sinceMs),
            t.textSecondary,
            null,
        )
        is VehicleStatus.Exited -> Triple(
            "하차 감지" + sinceText(now - status.detectedAtMs) + " · 눌러서 층수 기록",
            t.elapsedGreen,
            onOpenFloor,
        )
        VehicleStatus.Idle -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .let { m -> action?.let { m.clip(t.buttonShape).clickable(role = Role.Button, onClick = it) } ?: m }
            .semantics { if (action != null) contentDescription = "$text, 층수 기록 열기" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (status is VehicleStatus.Exited) t.elapsedGreen else t.primary))
        Spacer(Modifier.width(8.dp))
        Text(text, style = CarType.secondary.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

/** ‘ · 3분 전’처럼 짧게. 1분 미만·시각 없음이면 빈 문자열 */
private fun sinceText(ms: Long): String {
    if (ms < 60_000L || ms > 7L * 24 * 60 * 60 * 1000L) return ""
    return " · ${elapsedText(ms)} 전"
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
            Text(if (record == null) "층수 기록" else "주차 층수", style = CarType.secondary, color = t.onFeature)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label ?: "—", style = CarType.homeFloor, color = t.onFeature, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = t.onFeature, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun VehicleCard(
    hasRecord: Boolean,
    photoPath: String?,
    onOpenPhoto: (String) -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier,
) {
    val t = LocalCarTokens.current
    val photo = rememberPhoto(photoPath, maxSidePx = 640)
    Box(
        modifier
            .heightIn(min = 124.dp)
            .clip(t.cardShape)
            .cardSurface(t, t.cardShape)
            .let { m ->
                when {
                    // 사진이 있으면 미리보기를 눌러 크게 본다
                    photoPath != null -> m.clickable(role = Role.Button) { onOpenPhoto(photoPath) }
                        .semantics { contentDescription = "주차 사진 크게 보기" }
                    hasRecord -> m.clickable(role = Role.Button, onClick = onTakePhoto)
                        .semantics(mergeDescendants = true) { contentDescription = "주차 사진 찍기" }
                    else -> m
                }
            },
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            // 다시 찍기
            IconTarget(
                icon = R.drawable.ic_camera,
                description = "주차 사진 다시 찍기",
                onClick = onTakePhoto,
                tint = t.onPrimary,
                background = t.primary.copy(alpha = 0.82f),
                size = 48.dp,
                iconSize = 22.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        } else {
            Column(Modifier.padding(16.dp)) {
                Text("내 차량", style = CarType.secondary, color = t.black)
                Spacer(Modifier.height(8.dp))
                Image(
                    painterResource(t.vehicleRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = t.vehicleFilter,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = if (hasRecord) t.primary else t.inactive,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (hasRecord) "주차 사진 찍기" else "기록 후 사진 추가",
                        style = CarType.label.copy(fontWeight = FontWeight.Bold),
                        color = if (hasRecord) t.black else t.inactive,
                    )
                }
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
fun locationSummary(record: ParkingRecordEntity): String {
    if (record.latitude == null) return "위치 없음 · 오른쪽 아이콘으로 저장할 수 있어요"
    val accuracy = record.locationAccuracyMeters?.let { "오차 약 ${it.toInt()}m" } ?: "정확도 정보 없음"
    val source = when (record.locationSource) {
        LocationSource.AFTER_DISCONNECT -> "하차 직후 위치"
        LocationSource.MANUAL -> "기록 시 위치"
        LocationSource.SAVED -> "직접 저장한 위치"
        else -> "저장 위치"
    }
    return "$source · $accuracy"
}
