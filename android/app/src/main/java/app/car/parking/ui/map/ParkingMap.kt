package app.car.parking.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.car.parking.BuildConfig
import app.car.parking.R
import app.car.parking.data.location.CurrentLocationState
import app.car.parking.data.location.UnavailableReason
import app.car.parking.data.storage.ParkingRecordEntity
import app.car.parking.domain.floor.Floors
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.MapColors
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

private enum class MapConnection { NoKey, Connecting, Ready, AuthFailed }

/**
 * 홈 지도. 저장 P 핀(확정 기록)과 현재 위치(실시간 상태)를 분리해 그린다.
 * 지도 키가 없으면 빌드는 되고 ‘지도 미연결’ 상태를 표시한다. 도보 경로·예상 시간은 그리지 않는다.
 */
@Composable
fun ParkingMap(
    record: ParkingRecordEntity?,
    location: CurrentLocationState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    var connection by remember {
        mutableStateOf(if (BuildConfig.NAVER_MAP_KEY_ID.isBlank()) MapConnection.NoKey else MapConnection.Connecting)
    }
    var styleFailed by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<NaverMap?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    Box(modifier.clip(shape).background(MapColors.background)) {
        when (connection) {
            MapConnection.NoKey, MapConnection.AuthFailed -> MapUnavailable(
                record = record,
                message = if (connection == MapConnection.NoKey) "지도 미연결\n지도 키를 설정하면 실제 지도가 표시돼요" else "지도 인증에 실패했어요",
                onRetry = if (connection == MapConnection.AuthFailed) {
                    { connection = MapConnection.Connecting; retryKey++ }
                } else null,
            )
            else -> NaverMapHost(
                key = retryKey,
                onReady = { connection = MapConnection.Ready; map = it },
                onAuthFailed = { connection = MapConnection.AuthFailed; map = null },
                onStyleFailed = { styleFailed = true },
                onGesture = { following = false },
            )
        }

        val naverMap = map
        if (naverMap != null && connection == MapConnection.Ready) {
            ParkingOverlays(naverMap, record, location, following, LocalCarTokens.current.primary.toArgb())
        }

        if (styleFailed && connection == MapConnection.Ready) {
            MapChip("기본 지도", Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        if (record != null && record.latitude == null && connection != MapConnection.NoKey) {
            MapChip("저장된 주차 좌표 없음", Modifier.align(Alignment.TopStart).padding(12.dp))
        }

        val fix = location.usableFix
        val recenterLabel = when {
            fix != null -> "현재 위치로 이동"
            location is CurrentLocationState.Unavailable && location.reason == UnavailableReason.NoPermission -> "현재 위치 사용 불가, 위치 권한 없음"
            location is CurrentLocationState.Locating -> "현재 위치 확인 중"
            else -> "현재 위치 사용 불가"
        }
        if (connection == MapConnection.Ready) {
            val t = LocalCarTokens.current
            IconTarget(
                icon = R.drawable.ic_my_location,
                description = recenterLabel,
                onClick = {
                    val target = location.usableFix ?: return@IconTarget
                    following = true
                    map?.moveCamera(CameraUpdate.scrollTo(LatLng(target.latitude, target.longitude)).animate(CameraAnimation.Easing))
                },
                enabled = fix != null,
                tint = if (fix != null) t.black else t.inactive,
                background = t.white,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .shadow(3.dp, CircleShape),
            )
        }
    }
}

@Composable
private fun NaverMapHost(
    key: Int,
    onReady: (NaverMap) -> Unit,
    onAuthFailed: () -> Unit,
    onStyleFailed: () -> Unit,
    onGesture: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val mapView = remember(key) { MapView(context).apply { onCreate(Bundle()) } }

    DisposableEffect(key) {
        NaverMapSdk.getInstance(context).onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { onAuthFailed() }
        onDispose { NaverMapSdk.getInstance(context).onAuthFailedListener = null }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { naverMap ->
                // 휴대폰 언어와 관계없이 지명은 한국어로 표시한다
                naverMap.locale = java.util.Locale.KOREAN
                naverMap.uiSettings.apply {
                    isZoomControlEnabled = false
                    isLocationButtonEnabled = false
                    isCompassEnabled = false
                    isScaleBarEnabled = false
                    // SDK 로고·저작권은 유지하고 재중앙 버튼과 겹치지 않게 왼쪽 아래에 둔다
                    setLogoGravity(Gravity.BOTTOM or Gravity.START)
                    with(density) { setLogoMargin(12.dp.roundToPx(), 0, 0, 12.dp.roundToPx()) }
                }
                if (BuildConfig.NAVER_MAP_STYLE_ID.isNotBlank()) {
                    naverMap.setCustomStyleId(
                        BuildConfig.NAVER_MAP_STYLE_ID,
                        object : NaverMap.OnCustomStyleLoadCallback {
                            override fun onCustomStyleLoaded() = Unit
                            override fun onCustomStyleLoadFailed(e: Exception) {
                                // 스타일 실패 시 지원되는 기본 지도로 복구하고 오버레이는 유지한다
                                naverMap.customStyleId = null
                                onStyleFailed()
                            }
                        },
                    )
                }
                naverMap.addOnCameraChangeListener { reason, _ ->
                    if (reason == CameraUpdate.REASON_GESTURE) onGesture()
                }
                onReady(naverMap)
            }
            mapView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ParkingOverlays(
    map: NaverMap,
    record: ParkingRecordEntity?,
    location: CurrentLocationState,
    following: Boolean,
    pinColor: Int,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val marker = remember(map) { Marker() }
    val accuracy = remember(map) {
        CircleOverlay().apply {
            color = 0x295483D5
            outlineWidth = 0
        }
    }
    var cameraInitialized by remember(map) { mutableStateOf(false) }

    // 저장 P 핀: 확정 기록의 좌표만 따른다. 현재 위치로 옮기지 않는다
    val pinLat = record?.latitude
    val pinLng = record?.longitude
    val floor = Floors.label(record?.floorLevel)
    DisposableEffect(map, pinLat, pinLng, floor, pinColor) {
        if (pinLat != null && pinLng != null) {
            marker.position = LatLng(pinLat, pinLng)
            marker.icon = OverlayImage.fromBitmap(renderPin(floor, context.resources.displayMetrics.density, pinColor))
            marker.anchor = android.graphics.PointF(0.5f, 1f)
            marker.map = map
        } else {
            marker.map = null
        }
        onDispose { marker.map = null }
    }

    val fix = location.usableFix
    DisposableEffect(map, fix) {
        val overlay = map.locationOverlay
        if (fix != null) {
            val position = LatLng(fix.latitude, fix.longitude)
            overlay.position = position
            overlay.circleRadius = 0
            overlay.isVisible = true
            val radius = fix.accuracyMeters?.toDouble()
            if (radius != null && radius > 0) {
                accuracy.center = position
                accuracy.radius = radius
                accuracy.map = map
            } else {
                accuracy.map = null
            }
        } else {
            // 위치를 얻지 못하면 현재 위치 점을 숨긴다. 오래된 좌표도 현재 위치처럼 보여주지 않는다
            overlay.isVisible = false
            accuracy.map = null
        }
        onDispose { }
    }

    LaunchedEffect(map, pinLat, pinLng, fix != null) {
        if (cameraInitialized) return@LaunchedEffect
        val target = when {
            pinLat != null && pinLng != null -> LatLng(pinLat, pinLng)
            fix != null -> LatLng(fix.latitude, fix.longitude)
            else -> null
        } ?: return@LaunchedEffect
        map.moveCamera(CameraUpdate.scrollAndZoomTo(target, 16.5))
        cameraInitialized = true
    }

    LaunchedEffect(map, fix, following) {
        if (following && fix != null) {
            map.moveCamera(CameraUpdate.scrollTo(LatLng(fix.latitude, fix.longitude)).animate(CameraAnimation.Easing))
        }
    }
}

/** 검정 P 핀 + 작은 흰 층수 배지 */
private fun renderPin(floor: String?, density: Float, pinColor: Int): Bitmap {
    val w = (44 * density).toInt()
    val pinH = (52 * density).toInt()
    val badgeH = if (floor != null) (22 * density).toInt() else 0
    val bitmap = Bitmap.createBitmap(w, pinH + badgeH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinColor }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    val r = w / 2f - density
    val path = Path().apply {
        addCircle(w / 2f, r + density, r, Path.Direction.CW)
        moveTo(w / 2f - r * 0.55f, r * 1.75f)
        lineTo(w / 2f, pinH.toFloat() - density)
        lineTo(w / 2f + r * 0.55f, r * 1.75f)
        close()
    }
    c.drawPath(path, black)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 24 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    c.drawText("P", w / 2f, r + density + text.textSize * 0.36f, text)
    if (floor != null) {
        val top = pinH.toFloat() - 4 * density
        val rect = RectF(2 * density, top, w - 2 * density, top + badgeH - 2 * density)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E4E4.toInt() }
        c.drawRoundRect(RectF(rect.left - density, rect.top - density, rect.right + density, rect.bottom + density), 6 * density, 6 * density, outline)
        c.drawRoundRect(rect, 6 * density, 6 * density, white)
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF181818.toInt()
            textSize = 12 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(floor, w / 2f, rect.centerY() + badge.textSize * 0.36f, badge)
    }
    return bitmap
}

@Composable
private fun MapUnavailable(record: ParkingRecordEntity?, message: String, onRetry: (() -> Unit)?) {
    val t = LocalCarTokens.current
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = CarType.secondary, color = MapColors.label, textAlign = TextAlign.Center)
        if (record?.latitude != null) {
            Text(
                "저장 좌표 %.5f, %.5f".format(record.latitude, record.longitude),
                style = CarType.label,
                color = t.textSecondary,
            )
        }
        if (onRetry != null) {
            Box(
                Modifier
                    .clip(t.buttonShape)
                    .border(1.dp, t.border, t.buttonShape)
                    .background(t.white)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) { Text("다시 시도", style = CarType.secondary.copy(fontWeight = FontWeight.Bold), color = t.black) }
        }
    }
}

@Composable
private fun MapChip(text: String, modifier: Modifier) {
    val t = LocalCarTokens.current
    Box(modifier.clip(RoundedCornerShape(50)).background(t.white).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = CarType.label.copy(fontWeight = FontWeight.Bold), color = t.black)
    }
}
