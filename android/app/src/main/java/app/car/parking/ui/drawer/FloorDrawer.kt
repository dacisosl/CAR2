package app.car.parking.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.car.parking.R
import app.car.parking.data.storage.DrawerSide
import app.car.parking.domain.floor.FloorRecommendation
import app.car.parking.domain.floor.Floors
import app.car.parking.domain.parking.DrawerTarget
import app.car.parking.ui.DrawerUiState
import app.car.parking.ui.components.IconTarget
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.primarySurface
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HANDLE_HOLD_MS = 350L

@Composable
fun FloorDrawer(
    state: DrawerUiState,
    side: DrawerSide,
    onSideChange: (DrawerSide) -> Unit,
    onSelect: (Int) -> Unit,
    onCenter: (Int) -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onStatusBarChange: (Boolean) -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
) {
    val t = LocalCarTokens.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val fontScale = density.fontScale.coerceIn(1f, 1.3f)
        // 왼쪽 릴 + 오른쪽 주차 장면. 화면 폭의 90%(최대 400dp), 바깥을 눌러 닫을 자리는 남긴다
        val panelWidth = minOf(400.dp * fontScale, screenWidth * 0.9f)
        // 40sp 층 글자(가장 긴 ‘10F’·‘B10’) + 띠 여백. 남는 폭은 주차 장면에 준다
        val reelWidth = 100.dp * fontScale
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val screenPx = with(density) { screenWidth.toPx() }
        val dragX = remember(side) { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }

        // 배경 홈만 옅게 어둡게. 누르면 릴에서 마지막으로 멈춘 층으로 저장하고 닫는다
        Box(
            Modifier
                .fillMaxSize()
                .background(t.backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "선택한 층으로 저장하고 닫기",
                    onClick = onDismiss,
                ),
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            // 화면 전체 높이 대신 내용에 맞춘 높이. 작은 화면에서는 화면 안에 들어오게 줄인다
            val panelHeight = minOf(540.dp * fontScale, screenHeight * 0.9f)
            val panelShape = if (side == DrawerSide.Left) {
                RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            } else {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            }

            if (dragging) {
                // 드래그 중 반대쪽 고정 위치 표시
                Box(
                    Modifier
                        .align(if (side == DrawerSide.Left) Alignment.CenterEnd else Alignment.CenterStart)
                        .width(panelWidth)
                        .height(panelHeight)
                        .padding(6.dp)
                        .border(2.dp, if (t.dark) t.primary else t.white.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
                )
            }

            Column(
                Modifier
                    .align(if (side == DrawerSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset { IntOffset(dragX.value.roundToInt(), 0) }
                    .width(panelWidth)
                    .height(panelHeight)
                    .shadow(if (dragging) 16.dp else 8.dp, panelShape)
                    .clip(panelShape)
                    .background(t.white)
                    .let { m -> (t.accentSilver ?: t.cardBorder)?.let { m.border(1.dp, it, panelShape) } ?: m }
                    // 패널 면의 탭이 뒤 배경(저장 후 닫기)으로 넘어가지 않게만 막는다. 접근성 버튼으로 노출하지 않는다
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 16.dp),
            ) {
                val base = LocalViewConfiguration.current
                val holdConfig = remember(base) {
                    object : ViewConfiguration by base {
                        override val longPressTimeoutMillis: Long = HANDLE_HOLD_MS
                    }
                }
                CompositionLocalProvider(LocalViewConfiguration provides holdConfig) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .semantics {
                                contentDescription = "패널 이동 손잡이, 길게 눌러 좌우로 이동"
                                customActions = listOf(
                                    CustomAccessibilityAction(if (side == DrawerSide.Left) "오른쪽으로 이동" else "왼쪽으로 이동") {
                                        onSideChange(side.opposite()); true
                                    },
                                )
                            }
                            .pointerInput(side, panelWidthPx, screenPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        scope.launch { dragX.snapTo(dragX.value + amount.x) }
                                    },
                                    onDragEnd = {
                                        dragging = false
                                        val center = if (side == DrawerSide.Left) panelWidthPx / 2 + dragX.value
                                        else screenPx - panelWidthPx / 2 + dragX.value
                                        val switch = if (side == DrawerSide.Left) center > screenPx * 0.6f else center < screenPx * 0.4f
                                        if (switch) {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            onSideChange(side.opposite())
                                        } else {
                                            // 유효 영역 밖에서 놓으면 원래 쪽으로 복귀
                                            scope.launch { dragX.animateTo(0f) }
                                        }
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        scope.launch { dragX.animateTo(0f) }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(t.accentSilver ?: t.accent),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "주차 층수",
                        style = CarType.title,
                        color = t.black,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconTarget(icon = R.drawable.ic_close, description = "닫기, 저장하지 않음", onClick = onClose)
                }
                Text(
                    recommendationText(state),
                    style = CarType.secondary,
                    color = t.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 왼쪽 층수 릴, 오른쪽 주차 장면. 장면은 릴 가운데 띠와 같은 높이에 둔다.
                // 열린 패널이 다른 기록(새 하차 후보 등)으로 바뀌면 릴과 장면을 처음부터 다시 만든다
                key(state.target) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FloorReel(
                            selected = state.selectedLevel,
                            onSelect = onSelect,
                            onCenter = onCenter,
                            enabled = !state.saving,
                            modifier = Modifier.width(reelWidth).fillMaxHeight(),
                        )
                        ParkingScene(
                            level = state.selectedLevel,
                            fromUser = state.userTouched,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                }
                }

                // 상태바 스위치 + 작은 저장 버튼, 한 행 두 열. 둘 다 패널 안
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(t.buttonShape)
                            .border(1.dp, t.accentSilver ?: t.border, t.buttonShape)
                            .toggleable(value = state.statusBarOn, role = Role.Switch) { on ->
                                onStatusBarChange(on)
                                if (on && !notificationsAllowed) onRequestNotifications()
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "상태바에 층수 표시"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("상태바", style = CarType.label.copy(fontWeight = FontWeight.Bold), color = t.black)
                        Switch(
                            checked = state.statusBarOn,
                            onCheckedChange = null,
                            modifier = Modifier.scale(0.72f).height(28.dp),
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = t.primary,
                                checkedThumbColor = t.onPrimary,
                                checkedBorderColor = t.primary,
                                uncheckedTrackColor = t.border,
                                uncheckedThumbColor = t.white,
                                uncheckedBorderColor = t.border,
                            ),
                        )
                    }
                    val canSave = (state.selectedLevel ?: state.centerLevel) != null && !state.saving
                    Box(
                        Modifier
                            .width(92.dp)
                            .height(56.dp)
                            .clip(t.buttonShape)
                            .primarySurface(t, t.buttonShape, enabled = canSave)
                            .clickable(enabled = canSave, role = Role.Button, onClick = onSave)
                            .semantics {
                                contentDescription = if (canSave) "저장" else "저장, 층수를 먼저 선택하세요"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (state.saving) "저장 중" else "저장",
                            style = CarType.body.copy(fontWeight = FontWeight.Bold),
                            color = if (canSave) t.onFeature else t.inactive,
                        )
                    }
                }
                Text(
                    when {
                        state.statusBarOn && !notificationsAllowed -> "상태바 표시에 알림 권한이 필요해요"
                        state.statusBarOn -> "저장하면 상태바에 층수가 표시돼요"
                        else -> "상태바 표시 꺼짐"
                    },
                    style = CarType.label,
                    color = t.textSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            }
        }
    }
}

private fun recommendationText(state: DrawerUiState): String {
    val rec = state.recommendation
    if (rec is FloorRecommendation.Suggested) return "${Floors.label(rec.level)}로 예상돼요"
    if (state.target is DrawerTarget.Edit && state.selectedLevel != null) return "기록한 층수를 바꿀 수 있어요"
    if (state.recommendationPending && rec == null) return "층수를 확인하고 있어요"
    return "층수를 선택해주세요"
}

/**
 * 세로 층수 릴. 중앙 스냅, 한 층 단위 선택, 층을 넘을 때마다 가벼운 햅틱.
 * 손으로 돌리는 동안 중앙 층을 바로 선택값으로 올려, 패널 바깥을 눌러 닫아도 마지막 위치로 저장된다.
 * 릴 스크롤은 층수만 바꾸고 패널 이동 제스처와 분리되어 있다.
 */
@Composable
fun FloorReel(
    selected: Int?,
    onSelect: (Int) -> Unit,
    onCenter: (Int) -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val t = LocalCarTokens.current
    val haptic = LocalHapticFeedback.current
    val levels = remember { Floors.reel() }
    val initialIndex = remember { levels.indexOf(selected ?: 1).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialIndex)
    val scope = rememberCoroutineScope()
    var userDragging by remember { mutableStateOf(false) }

    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: initialIndex
        }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) userDragging = true
        }
    }
    // 띠에 있는 층을 알린다(아직 고르지 않았을 때 저장 버튼이 쓴다)
    LaunchedEffect(centerIndex) { levels.getOrNull(centerIndex)?.let(onCenter) }
    // 손으로 돌리는 중에는 중앙에 온 층을 바로 선택한다
    LaunchedEffect(centerIndex) {
        if (userDragging) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            levels.getOrNull(centerIndex)?.let(onSelect)
        }
    }
    // 스크롤이 멈추면 중앙 층으로 확정
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userDragging) {
            userDragging = false
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            levels.getOrNull(centerIndex)?.let(onSelect)
        }
    }
    // 저장 중(enabled=false)에는 흐르던 릴을 멈추고 저장한 층으로 돌려 둔다. 띠와 기록이 어긋나지 않게
    LaunchedEffect(enabled) {
        if (enabled) return@LaunchedEffect
        listState.stopScroll(MutatePriority.PreventUserInput)
        userDragging = false // 되돌아가는 동안 층 넘김 진동·선택을 내지 않는다
        val index = selected?.let { levels.indexOf(it) }?.takeIf { it >= 0 } ?: centerIndex
        listState.glideTo(index)
    }
    // 추천·기존 기록 등 외부 선택 변경을 릴에 반영
    LaunchedEffect(selected) {
        val index = selected?.let { levels.indexOf(it) } ?: return@LaunchedEffect
        if (index >= 0 && index != centerIndex && !userDragging) listState.glideTo(index)
    }

    // 놓으면 시스템 관성으로 흐르다 가까운 층에 살짝 감속하며 딱 맞춘다
    val decay = rememberSplineBasedDecay<Float>()
    val flingBehavior = remember(listState, decay) {
        snapFlingBehavior(
            snapLayoutInfoProvider = SnapLayoutInfoProvider(listState, SnapPosition.Center),
            decayAnimationSpec = decay,
            snapAnimationSpec = ReelSnapSpec,
        )
    }

    BoxWithConstraints(modifier) {
        val itemHeight = 60.dp
        val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
        val pad = ((maxHeight - itemHeight) / 2).coerceAtLeast(0.dp)
        // 중앙 선택 띠. 처음부터 채워서 지금 어느 층을 가리키는지 바로 보이게 한다
        val filled = true
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(t.buttonShape)
                .let { if (filled) it.primarySurface(t, t.buttonShape) else it.border(2.dp, t.black, t.buttonShape) },
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = pad),
            flingBehavior = flingBehavior,
            // 저장 중에는 이미 기록한 층과 어긋나지 않게 돌리지 않는다
            userScrollEnabled = enabled,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(levels, key = { _, level -> level }) { index, level ->
                val label = Floors.label(level)!!
                // 중앙에서 몇 칸 떨어졌는지(소수). 그리기 단계에서만 읽어 스크롤마다 재구성하지 않는다
                val distance = { (listState.centerDelta(index) ?: (itemPx * 3)) / itemPx }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .drawBehind {
                            // 층 사이 구분선. 중앙에서 멀어질수록 옅게
                            val d = abs(distance())
                            if (d > 0.5f) {
                                drawLine(
                                    color = t.border.copy(alpha = (1.2f - d * 0.3f).coerceIn(0.15f, 1f)),
                                    start = Offset(12.dp.toPx(), size.height),
                                    end = Offset(size.width - 12.dp.toPx(), size.height),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                        }
                        .clickable(enabled = enabled, role = Role.Button) {
                            onSelect(level)
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            scope.launch { listState.glideTo(index) }
                        }
                        .semantics {
                            contentDescription = label
                            this.selected = selected == level
                            stateDescription = if (selected == level) "선택됨" else ""
                        },
                ) {
                    // 같은 글자를 두 번 그린다: 띠 밖은 검정, 띠 안은 띠 위 글자색. 경계에서 색이 정확히 갈린다
                    ReelLabel(label, t.black, distance, insideBand = false)
                    ReelLabel(label, if (filled) t.onFeature else t.black, distance, insideBand = true)
                }
            }
        }
    }
}

private val ReelSnapSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

@Composable
private fun BoxScope.ReelLabel(label: String, color: Color, distance: () -> Float, insideBand: Boolean) {
    Box(
        Modifier
            .matchParentSize()
            .drawWithContent {
                // 띠는 릴 중앙 한 칸. 이 항목 좌표에서 띠의 위치로 잘라 그린다
                val bandTop = -distance() * size.height
                clipRect(
                    top = bandTop,
                    bottom = bandTop + size.height,
                    clipOp = if (insideBand) ClipOp.Intersect else ClipOp.Difference,
                ) { this@drawWithContent.drawContent() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            style = CarType.selectedFloor,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val d = abs(distance()).coerceAtMost(3f)
                val scale = 1f - 0.16f * d.coerceAtMost(2.5f)
                scaleX = scale
                scaleY = scale
                alpha = if (insideBand) 1f else (1f - 0.3f * d).coerceAtLeast(0.18f)
            },
        )
    }
}

/** 항목 중심이 릴 중앙에서 떨어진 거리(px). 화면 밖이면 null */
private fun LazyListState.centerDelta(index: Int): Float? {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return item.offset + item.size / 2f - center
}

/** 보이는 항목이면 부드럽게 미끄러져 중앙에 맞추고, 멀리 있으면 바로 옮긴다 */
private suspend fun LazyListState.glideTo(index: Int) {
    val delta = centerDelta(index)
    if (delta != null) animateScrollBy(delta, ReelSnapSpec) else scrollToItem(index)
}
