package app.car.parking.ui.drawer

import android.animation.ValueAnimator
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.car.parking.R
import app.car.parking.domain.floor.Floors
import app.car.parking.platform.statusbar.FloorSign
import app.car.parking.ui.theme.LocalCarTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 릴이 멈출 때까지 기다리는 시간. 그 사이 층이 다시 바뀌면 새기기를 새로 시작한다 */
private const val SETTLE_MS = 220L

/** 차가 다 들어온 뒤 표지판 쪽으로 다가가는 배율과, 표지판 중심이 옮겨 갈 장면 안 위치(0~1). 세로 0.5 = 릴 가운데 띠 높이 */
private const val ZOOM = 1.9f
private const val ZOOM_TARGET_X = 0.6f
private const val ZOOM_TARGET_Y = 0.5f

/**
 * 사이드바 오른쪽의 주차 장면. 차가 기둥 옆에 후진 주차하는 선화 영상(5초)을 한 번 재생하고,
 * 층을 고르면 기둥 표지판에 그 층이 새겨진다:
 * 표지판이 층 색으로 차오르고 → 글자가 왼쪽부터 새겨지고 → 빛이 훑고 지나가며 모서리에 별빛이 반짝인다.
 * 표지판 위치는 영상 프레임마다 추적한 값([ParkingSignTrack])을 따라가므로 영상 도중에 골라도 기둥에 붙어 있다.
 * 차가 다 들어오고 층이 정해지면 표지판 쪽으로 천천히 다가가 글자가 잘 보이게 한다.
 *
 * 영상은 흑백 선화다. 테마의 바탕·글자 색으로 다시 칠해 패널과 이어지게 한다.
 * 영상이 준비되기 전에는 같은 첫 프레임 그림을 깔아 두어 빈 화면이 보이지 않고,
 * ‘애니메이션 삭제’ 설정이거나 재생할 수 없으면 주차가 끝난 장면만 정지 화면으로 보여 준다.
 * 장식 요소라 접근성 트리에서는 숨긴다(층은 릴과 저장 버튼이 알려 준다).
 */
@Composable
fun ParkingScene(level: Int?, fromUser: Boolean, modifier: Modifier = Modifier) {
    val t = LocalCarTokens.current
    val haptic = LocalHapticFeedback.current
    val still = remember { !ValueAnimator.areAnimatorsEnabled() }
    // 영상이 끝났거나(재생 실패 포함) 재생하지 않는 경우. 이때는 마지막 장면 그림을 보여 주고 재생기를 놓는다
    // (끝난 뒤 디코더가 마지막 프레임 버퍼를 놓아 영상 면이 비는 기기가 있어, 끝 장면을 영상에 맡기지 않는다)
    var ended by remember { mutableStateOf(still) }
    val player = remember { PlayerRef() }
    // 화면에 실제로 그려진 영상 프레임의 시각. 표지판 위치는 재생 시계가 아니라 보이는 프레임을 따른다
    // (기기가 느려 프레임이 밀려도 글자가 표지판에서 벗어나지 않는다)
    var videoMs by remember { mutableFloatStateOf(0f) }

    // 새기기 상태
    var shown by remember { mutableStateOf<Int?>(null) }
    val plate = remember { Animatable(0f) }
    val carve = remember { Animatable(0f) }
    val glint = remember { Animatable(0f) }
    val sparkle = remember { Animatable(0f) }
    val zoom = remember { Animatable(0f) }
    val sign = FloorSign.of(shown)
    val plateColor by animateColorAsState(Color(sign.plate), tween(220), label = "plate")

    LaunchedEffect(level) {
        // 릴을 다시 돌리면 진행 중이던 빛·별은 바로 지운다
        glint.snapTo(0f)
        sparkle.snapTo(0f)
        if (level == null || (level == shown && carve.value >= 1f)) return@LaunchedEffect
        delay(SETTLE_MS)
        if (still) {
            shown = level
            plate.snapTo(1f)
            carve.snapTo(1f)
            return@LaunchedEffect
        }
        if (carve.value > 0f) carve.animateTo(0f, tween(110)) // 이전 글자를 빠르게 지운다
        shown = level
        if (plate.value < 1f) plate.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        carve.animateTo(1f, tween(460, easing = LinearEasing))
        // 사용자가 고른 층만 진동. 추천·기존 기록처럼 저절로 새겨지는 층에는 주지 않는다
        if (fromUser) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        coroutineScope {
            launch { glint.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
            sparkle.animateTo(1f, tween(820, easing = LinearEasing))
        }
    }
    LaunchedEffect(ended, shown != null) {
        if (!ended || shown == null) return@LaunchedEffect
        if (still) zoom.snapTo(1f) else zoom.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }

    val ink = t.black
    val paper = t.white
    val matrix = remember(ink, paper) { themeMatrix(ink, paper) }
    val quad = remember { FloatArray(6) }
    val anchor = remember { FloatArray(6) }
    val paint = remember { labelPaint() }
    val bounds = remember { Rect() }

    // 받은 칸 안에 비율을 지켜 들어가게 한다(가로 화면처럼 높이가 모자라면 폭을 줄인다). 가운데 정렬은 릴 띠 높이에 맞추기 위해
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(ParkingSignTrack.VIDEO_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .background(paper)
                .clearAndSetSemantics { },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 보이는 프레임의 표지판 중심을 ZOOM배 키우며 목표 위치로 옮긴다. zoom=0이면 그대로
                        val z = zoom.value
                        val scale = 1f + (ZOOM - 1f) * z
                        ParkingSignTrack.at(if (ended) Float.MAX_VALUE else videoMs, anchor)
                        val fx = (anchor[0] + anchor[1]) / 2
                        val fy = (anchor[2] + anchor[3] + anchor[4] + anchor[5]) / 4
                        val tx = fx + (ZOOM_TARGET_X - fx) * z
                        val ty = fy + (ZOOM_TARGET_Y - fy) * z
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = (tx - fx * scale) * size.width
                        translationY = (ty - fy * scale) * size.height
                    },
            ) {
                Image(
                    painterResource(if (ended) R.drawable.parking_last else R.drawable.parking_first),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)),
                    modifier = Modifier.fillMaxSize(),
                )
                if (!ended) {
                    ParkingVideo(
                        matrix = matrix,
                        player = player,
                        // 끝난 뒤 재생 위치를 0으로 돌려주는 기기가 있어 앞으로만 움직인다
                        onFrame = { videoMs = maxOf(videoMs, it) },
                        onEnded = { ended = true },
                        onError = { ended = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val label = Floors.label(shown) ?: return@Canvas
                    ParkingSignTrack.at(if (ended) Float.MAX_VALUE else videoMs, quad)
                    drawEngraving(
                        q = quad,
                        label = label,
                        plateColor = plateColor,
                        textColor = Color(sign.text),
                        ink = ink,
                        paper = paper,
                        plate = plate.value,
                        carve = carve.value,
                        glint = glint.value,
                        sparkle = sparkle.value,
                        paint = paint,
                        bounds = bounds,
                    )
                }
            }
            // 다가갈수록 가장자리를 바탕색으로 흐려 잘린 선이 패널에 스며들게 한다
            Canvas(Modifier.fillMaxSize()) { drawEdgeFade(paper, 18.dp.toPx() * zoom.value) }
        }
    }
}

private fun DrawScope.drawEngraving(
    q: FloatArray,
    label: String,
    plateColor: Color,
    textColor: Color,
    ink: Color,
    paper: Color,
    plate: Float,
    carve: Float,
    glint: Float,
    sparkle: Float,
    paint: android.graphics.Paint,
    bounds: Rect,
) {
    val lx = q[0] * size.width
    val rx = q[1] * size.width
    val tl = q[2] * size.height
    val tr = q[3] * size.height
    val bl = q[4] * size.height
    val br = q[5] * size.height
    val width = rx - lx
    if (width <= 1f) return
    val top = min(tl, tr)
    val bottom = max(bl, br)
    val signH = ((bl + br) - (tl + tr)) / 2
    val skew = ((tr - tl) + (br - bl)) / 2 / width // 원근으로 기울어진 정도(세로 기울임)
    val cx = (lx + rx) / 2
    val cy = (tl + tr + bl + br) / 4
    val face = Path().apply {
        moveTo(lx, tl)
        lineTo(rx, tr)
        lineTo(rx, br)
        lineTo(lx, bl)
        close()
    }

    if (plate > 0f) {
        clipPath(face) {
            // 1) 표지판이 아래에서 위로 층 색으로 차오른다
            val fillTop = bottom - (bottom - top) * plate
            drawRect(plateColor, Offset(lx, fillTop), Size(width, bottom - fillTop))

            // 2) 글자가 왼쪽부터 새겨진다. 새기는 끝을 작은 불빛이 따라간다
            if (carve > 0f) {
                paint.textSize = 100f
                paint.getTextBounds(label, 0, label.length, bounds)
                paint.textSize = 100f * min(width * 0.74f / bounds.width(), signH * 0.5f / bounds.height())
                paint.getTextBounds(label, 0, label.length, bounds)
                // 글자 잉크(좌우 여백 제외)의 가운데를 표지판 가운데에 둔다. ‘1’처럼 왼쪽 여백이 큰 글자도 치우치지 않게
                val inkW = bounds.width().toFloat()
                val textX = -(bounds.left + bounds.right) / 2f
                val baseline = -(bounds.top + bounds.bottom) / 2f
                val depth = signH * 0.025f
                val inkL = -inkW / 2 - 1f
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.translate(cx, cy)
                    nc.skew(0f, skew)
                    nc.clipRect(inkL, -signH, inkL + (inkW + depth + 2f) * carve, signH)
                    // 파인 홈의 그늘 → 글자
                    paint.color = android.graphics.Color.argb(100, 0, 0, 0)
                    nc.drawText(label, textX + depth, baseline + depth, paint)
                    paint.color = textColor.toArgb()
                    nc.drawText(label, textX, baseline, paint)
                    nc.restore()
                }
                if (carve < 1f) {
                    val x = cx - inkW / 2 + inkW * carve
                    val y = cy + skew * (x - cx)
                    val r = signH * 0.3f
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), Offset(x, y), r),
                        radius = r,
                        center = Offset(x, y),
                    )
                }
            }

            // 3) 다 새기면 빛이 비스듬히 훑고 지나간다
            if (glint > 0f && glint < 1f) {
                val band = width * 0.7f
                val gx = lx - band + (width + 2 * band) * glint
                val alpha = sin(PI * glint).toFloat() * 0.75f
                val vx = 0.87f * band / 2
                val vy = 0.5f * band / 2
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = alpha),
                        1f to Color.Transparent,
                        start = Offset(gx - vx, cy - vy),
                        end = Offset(gx + vx, cy + vy),
                    ),
                    Offset(lx, top),
                    Size(width, bottom - top),
                )
            }
        }
    }

    // 4) 기둥에서 반짝: 표지판 오른쪽 위 → 왼쪽 아래 모서리 순서로 별빛이 켜졌다 꺼진다
    if (sparkle > 0f && sparkle < 1f) {
        val r = signH * 0.34f
        drawSparkle(Offset(rx + r * 0.2f, tr - r * 0.05f), r, (sparkle / 0.75f).coerceIn(0f, 1f), ink, paper)
        drawSparkle(Offset(lx - r * 0.1f, bl - r * 0.15f), r * 0.6f, ((sparkle - 0.25f) / 0.75f).coerceIn(0f, 1f), ink, paper)
    }
}

/** 네 갈래 별. 선화에 맞춰 바탕색으로 채우고 글자색 선을 두른다 */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, progress: Float, ink: Color, paper: Color) {
    if (progress <= 0f || progress >= 1f) return
    val r = radius * sin(PI * progress).toFloat()
    if (r < 0.5f) return
    val turn = PI / 4 * progress
    val star = Path()
    for (i in 0 until 8) {
        val angle = PI / 4 * i - PI / 2 + turn
        val rr = if (i % 2 == 0) r else r * 0.26f
        val x = center.x + (cos(angle) * rr).toFloat()
        val y = center.y + (sin(angle) * rr).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, paper)
    drawPath(star, ink, style = Stroke(width = max(1.5f, r * 0.12f), join = StrokeJoin.Round))
}

private fun DrawScope.drawEdgeFade(color: Color, width: Float) {
    if (width <= 0f) return
    val clear = color.copy(alpha = 0f)
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(color, clear), startY = 0f, endY = width), Offset.Zero, Size(w, width))
    drawRect(Brush.verticalGradient(listOf(clear, color), startY = h - width, endY = h), Offset(0f, h - width), Size(w, width))
    drawRect(Brush.horizontalGradient(listOf(color, clear), startX = 0f, endX = width), Offset.Zero, Size(width, h))
    drawRect(Brush.horizontalGradient(listOf(clear, color), startX = w - width, endX = w), Offset(w - width, 0f), Size(width, h))
}

private fun labelPaint() = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
}

/** 흑백 선화를 테마 색으로: 흰 바탕 → paper, 검은 선 → ink (채널별 선형 대응). 오프셋은 0~255 단위 */
private fun themeMatrix(ink: Color, paper: Color): FloatArray {
    val i = ink.toArgb()
    val p = paper.toArgb()
    fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF).toFloat()
    return floatArrayOf(
        (ch(p, 16) - ch(i, 16)) / 255f, 0f, 0f, 0f, ch(i, 16),
        0f, (ch(p, 8) - ch(i, 8)) / 255f, 0f, 0f, ch(i, 8),
        0f, 0f, (ch(p, 0) - ch(i, 0)) / 255f, 0f, ch(i, 0),
        0f, 0f, 0f, 1f, 0f,
    )
}

/** 재생기와 상태. 화면이 보일 때만 재생하고, 가려지면 멈췄다가 다시 보이면 이어서 재생한다 */
private class PlayerRef {
    var player: MediaPlayer? = null
    var prepared = false
    var completed = false
    var visible = false

    /** 재생 위치(ms). 준비 전이면 null */
    fun positionMs(): Float? {
        val p = player ?: return null
        if (!prepared) return null
        return runCatching { p.currentPosition.toFloat() }.getOrNull()
    }

    fun sync() {
        val p = player ?: return
        if (!prepared || completed) return
        runCatching {
            if (visible && !p.isPlaying) p.start() else if (!visible && p.isPlaying) p.pause()
        }
    }

    fun release() {
        val p = player ?: return
        player = null
        prepared = false
        runCatching { p.release() }
    }
}

/**
 * 영상 재생(한 번). TextureView를 써서 패널의 둥근 모서리·확대 변환·색 입히기를 그대로 따른다
 * (SurfaceView는 지도 표면과 겹치는 순서와 변환이 보장되지 않는다).
 */
@Composable
private fun ParkingVideo(
    matrix: FloatArray,
    player: PlayerRef,
    onFrame: (Float) -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame by rememberUpdatedState(onFrame)
    val ended by rememberUpdatedState(onEnded)
    val error by rememberUpdatedState(onError)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        player.visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    player.visible = true
                    player.sync()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    player.visible = false
                    player.sync()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false // 첫 프레임 전에는 투명해서 아래의 첫 장면 그림이 보인다
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        val s = Surface(st).also { surface = it }
                        // 첫 그리기 도중에 불린다. 패널 첫 프레임을 늦추지 않게 다음 차례에 재생기를 준비한다
                        post {
                            if (surface !== s || player.player != null || player.completed) return@post
                            val p = MediaPlayer()
                            player.player = p
                            runCatching {
                                p.setDataSource(ctx, Uri.parse("android.resource://${ctx.packageName}/${R.raw.parking_reverse}"))
                                p.setSurface(s)
                                p.setVolume(0f, 0f)
                                p.setOnPreparedListener {
                                    player.prepared = true
                                    player.sync()
                                }
                                p.setOnCompletionListener {
                                    player.completed = true
                                    ended()
                                }
                                p.setOnErrorListener { _, _, _ ->
                                    player.release()
                                    error()
                                    true
                                }
                                p.prepareAsync()
                            }.onFailure {
                                player.release()
                                error()
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        player.release()
                        surface?.release()
                        surface = null
                        return true
                    }

                    // 새 프레임이 화면에 올라올 때마다 그 시각을 알린다
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                        player.positionMs()?.let(frame)
                    }
                }
            }
        },
        update = { view ->
            view.setLayerPaint(android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        },
    )
}
