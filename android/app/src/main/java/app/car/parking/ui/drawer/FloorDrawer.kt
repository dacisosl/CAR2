package app.car.parking.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.sp
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
    onClose: () -> Unit,
    onSave: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    val t = LocalCarTokens.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val fontScale = density.fontScale.coerceIn(1f, 1.3f)
        // 약 200~216dp, 화면 폭의 60% 이내. 큰 글자에서는 필요한 폭을 더 확보한다
        val panelWidth = minOf(216.dp * fontScale, screenWidth * 0.6f).coerceAtLeast(184.dp)
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val screenPx = with(density) { screenWidth.toPx() }
        val dragX = remember(side) { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }

        // 배경 홈만 옅게 어둡게. 누르면 저장하지 않고 닫는다
        Box(
            Modifier
                .fillMaxSize()
                .background(t.backdrop)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )

        if (dragging) {
            // 드래그 중 반대쪽 고정 위치 표시
            Box(
                Modifier
                    .align(if (side == DrawerSide.Left) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .border(2.dp, t.white.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
            )
        }

        Column(
            Modifier
                .align(if (side == DrawerSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                .width(panelWidth)
                .fillMaxHeight()
                .shadow(if (dragging) 16.dp else 8.dp)
                .background(t.white)
                .let { m ->
                    t.accentSilver?.let {
                        m.border(
                            1.dp,
                            it,
                            RoundedCornerShape(0.dp),
                        )
                    } ?: m
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .safeDrawingPadding()
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
                        .height(40.dp)
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

            FloorReel(
                selected = state.selectedLevel,
                onSelect = onSelect,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            // 카메라 아이콘 버튼 + 작은 저장 버튼, 한 행 두 열. 둘 다 패널 안
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var menu by remember { mutableStateOf(false) }
                val hasPhoto = state.pendingPhotoPath != null
                Box {
                    IconTarget(
                        icon = R.drawable.ic_camera,
                        description = if (hasPhoto) "주차 사진 추가, 사진 선택됨" else "주차 사진 추가",
                        onClick = { menu = true },
                        shape = t.buttonShape,
                        background = t.white,
                        border = t.accentSilver ?: t.border,
                        iconSize = 24.dp,
                    )
                    if (hasPhoto) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(t.primary),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("사진 촬영") }, onClick = { menu = false; onTakePhoto() })
                        DropdownMenuItem(text = { Text("앨범에서 선택") }, onClick = { menu = false; onPickPhoto() })
                    }
                }
                val canSave = state.selectedLevel != null && !state.saving
                Box(
                    Modifier
                        .weight(1f)
                        .widthIn(max = 96.dp)
                        .height(48.dp)
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
                        color = if (canSave) t.onPrimary else t.inactive,
                    )
                }
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
 * 세로 층수 릴. 중앙 스냅, 한 층 단위 선택, 가벼운 햅틱.
 * 릴 스크롤은 층수만 바꾸고 패널 이동 제스처와 분리되어 있다.
 */
@Composable
fun FloorReel(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
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
    LaunchedEffect(centerIndex) {
        if (userDragging) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    // 사용자의 스크롤이 멈추면 중앙 층을 선택
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userDragging) {
            userDragging = false
            levels.getOrNull(centerIndex)?.let { if (it != selected) onSelect(it) }
        }
    }
    // 추천·기존 기록 등 외부 선택 변경을 릴에 반영
    LaunchedEffect(selected) {
        val index = selected?.let { levels.indexOf(it) } ?: return@LaunchedEffect
        if (index >= 0 && index != centerIndex && !userDragging) listState.animateScrollToItem(index)
    }

    BoxWithConstraints(modifier) {
        val itemHeight = 64.dp
        val pad = ((maxHeight - itemHeight) / 2).coerceAtLeast(0.dp)
        // 중앙 선택 띠
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(t.buttonShape)
                .let { if (selected != null) it.primarySurface(t, t.buttonShape) else it.border(1.dp, t.border, t.buttonShape) },
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = pad),
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(levels, key = { _, level -> level }) { index, level ->
                val distance = abs(index - centerIndex)
                val label = Floors.label(level)!!
                val color = when {
                    distance == 0 && selected != null -> t.onPrimary
                    distance == 0 -> t.black
                    distance == 1 -> t.black
                    distance == 2 -> t.textSecondary
                    else -> t.inactive
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable(role = Role.Button) {
                            onSelect(level)
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                        .semantics {
                            contentDescription = label
                            this.selected = selected == level
                            stateDescription = if (selected == level) "선택됨" else ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = color,
                        style = when (distance) {
                            0 -> CarType.selectedFloor
                            1 -> CarType.title.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            else -> CarType.title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
