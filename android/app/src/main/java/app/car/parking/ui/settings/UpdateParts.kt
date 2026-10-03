package app.car.parking.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.car.parking.BuildConfig
import app.car.parking.ui.UpdateState
import app.car.parking.ui.theme.CarType
import app.car.parking.ui.theme.LocalCarTokens
import app.car.parking.ui.theme.primarySurface

fun updateMessage(state: UpdateState): String? = when (state) {
    UpdateState.Idle -> null
    UpdateState.Checking -> "새 버전을 확인하고 있어요"
    UpdateState.UpToDate -> "최신 버전이에요"
    is UpdateState.Available -> "새 버전 ${state.info.versionName}이 있어요"
    is UpdateState.Downloading -> "내려받는 중 ${(state.progress * 100).toInt()}%"
    is UpdateState.Ready -> "설치 화면에서 [설치]를 누르세요. 설치 허용을 묻는다면 허용한 뒤 다시 눌러 주세요"
    is UpdateState.Failed -> state.message
}

/**
 * 릴리스 노트에서 ‘이번 버전’ 항목만 골라 마크다운 기호 없이 보여준다.
 * 해당 절이 없으면 앞부분을 쓴다.
 */
fun releaseHighlights(markdown: String): String {
    val lines = markdown.lines().map { it.trim() }
    val start = lines.indexOfFirst { it.startsWith("#") && it.contains("이번 버전") }
    val section = if (start >= 0) {
        lines.drop(start + 1).takeWhile { !it.startsWith("#") }
    } else {
        lines
    }
    return section
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.replace("`", "").replace("**", "").replaceFirst(Regex("^[-*] "), "· ") }
        .take(8)
        .joinToString("\n")
}

/** 설정 맨 아래 버전 줄: 현재 버전 + 업데이트 확인/설치 */
@Composable
fun UpdateRow(state: UpdateState, onCheck: () -> Unit, onInstall: () -> Unit) {
    val t = LocalCarTokens.current
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "주차기록 ${BuildConfig.VERSION_NAME}" + if (BuildConfig.NAVER_MAP_KEY_ID.isBlank()) " · 지도 미연결" else "",
                style = CarType.secondary,
                color = t.textSecondary,
                modifier = Modifier.weight(1f),
            )
            val label = if (!BuildConfig.SELF_UPDATE) null else when (state) {
                is UpdateState.Available -> "업데이트"
                is UpdateState.Ready -> "설치"
                is UpdateState.Checking, is UpdateState.Downloading -> null
                else -> "업데이트 확인"
            }
            val action = if (state is UpdateState.Available || state is UpdateState.Ready) onInstall else onCheck
            if (label != null) {
                val highlight = state is UpdateState.Available || state is UpdateState.Ready
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(t.buttonShape)
                        .let { if (highlight) it.primarySurface(t, t.buttonShape) else it }
                        .clickable(role = Role.Button, onClick = action)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = CarType.secondary.copy(fontWeight = FontWeight.Bold),
                        color = if (highlight) t.onFeature else t.black,
                    )
                }
            }
        }
        updateMessage(state)?.let {
            Text(it, style = CarType.label, color = t.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        if (state is UpdateState.Downloading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                color = t.primary,
                trackColor = t.border,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 앱을 열 때 새 버전이 있으면 한 번 알려 준다 */
@Composable
fun UpdateDialog(state: UpdateState, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val t = LocalCarTokens.current
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (info != null) "새 버전 ${info.versionName}" else "업데이트") },
        text = {
            Column {
                info?.notes?.let(::releaseHighlights)?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        notes,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        style = CarType.secondary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                updateMessage(state)?.let { Text(it, style = CarType.label, color = t.textSecondary) }
                if (state is UpdateState.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = t.primary,
                        trackColor = t.border,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (state is UpdateState.Available || state is UpdateState.Ready) {
                TextButton(onClick = onInstall) { Text(if (state is UpdateState.Ready) "설치" else "업데이트", color = t.black) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("나중에", color = t.textSecondary) } },
    )
}
