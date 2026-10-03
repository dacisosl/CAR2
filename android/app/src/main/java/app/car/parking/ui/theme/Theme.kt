package app.car.parking.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.car.parking.R
import app.car.parking.data.storage.AppThemeId

/**
 * 디자인 토큰. 그래파이트·포레스트·에스프레소는 docs/FINISH_OPTIONS.md의 팔레트,
 * 실버는 이전 스틸 포인트(블랙·화이트 + 얇은 실버 디테일)다.
 * UHD는 유일한 어두운 테마다: 블랙·차콜 바탕, 차콜 그라데이션 카드 + 얇은 테두리, 네온 핑크 포인트.
 * 이름은 그대로 두고 [white]=바탕, [black]=본문 글자로 읽는다(UHD에서는 각각 블랙·밝은 회색).
 *
 * [primary]는 로고·층수 카드·선택 띠·저장 버튼·활성 BT 같은 면, [black]은 본문 글자다.
 */
@Immutable
data class CarTokens(
    val id: AppThemeId,
    val label: String,
    val description: String,
    val primary: Color,
    /** 본문 글자 */
    val black: Color,
    /** 화면 바탕 */
    val white: Color,
    /** 차량 카드 등 보조 면 */
    val surface: Color,
    val textSecondary: Color,
    val inactive: Color,
    val border: Color,
    val backdrop: Color = Color(0x47181818),
    val onPrimary: Color = Color.White,
    /** 팔레트의 세 번째 색(실버·스톤·페블). 스와치와 손잡이에 사용 */
    val accent: Color,
    /** [primarySurface] 위의 글자·아이콘. 없으면 [onPrimary] */
    val onPrimarySurface: Color? = null,
    /** 주요 면을 단색 대신 이 그라데이션으로 칠한다(UHD 차콜) */
    val primaryBrush: Brush? = null,
    /** 보조 카드의 은은한 그라데이션·얇은 테두리(UHD) */
    val surfaceBrush: Brush? = null,
    val cardBorder: Color? = null,
    /** 경과 시간 2시간·3시간 색. 어두운 바탕에서는 밝게 바꾼다 */
    val elapsedGreen: Color = Color(0xFF1E7A4C),
    val elapsedBurgundy: Color = Color(0xFF8E2A3B),
    /** 어두운 테마. Material 색 구성과 시스템 바 아이콘 명암을 바꾼다 */
    val dark: Boolean = false,
    /** 홈 경과 시간 옆 계기판(UHD) */
    val dial: Boolean = false,
    /** 실버 테마 전용. 얇은 테두리·손잡이·차량 선에만 사용 */
    val accentSilver: Color? = null,
    /** 주요 면의 얇은 테두리 */
    val primaryHairline: Color? = null,
    /** 주요 면 위쪽의 아주 약한 광택 */
    val gloss: Boolean = true,
    val cardShape: Shape,
    val buttonShape: Shape,
    /** 층수 카드 모양. 실버는 작은 사선 모서리 */
    val featureCardShape: Shape,
    @param:DrawableRes val vehicleRes: Int,
    /** PNG 측면 차량·로고를 팔레트 색으로 칠한다. 실버 벡터 차량은 원색 유지 */
    val vehicleTinted: Boolean = true,
) {
    val isSilver get() = id == AppThemeId.Silver

    val primaryBorder: BorderStroke? get() = primaryHairline?.let { BorderStroke(1.dp, it) }

    /** 층수 카드·선택 띠·저장 버튼처럼 [primarySurface]로 칠한 면 위의 글자색 */
    val onFeature: Color get() = onPrimarySurface ?: onPrimary

    val logoFilter: ColorFilter get() = ColorFilter.tint(primary)
    val vehicleFilter: ColorFilter? get() = if (vehicleTinted) ColorFilter.tint(primary) else null

    companion object {
        val Graphite = CarTokens(
            id = AppThemeId.Graphite,
            label = "그래파이트",
            description = "차콜 · 펄 화이트 · 실버",
            primary = Color(0xFF242629),
            black = Color(0xFF1E1F21),
            white = Color(0xFFFAFAF7),
            surface = Color(0xFFEFEFEC),
            textSecondary = Color(0xFF65676A),
            inactive = Color(0xFFA9ABAE),
            border = Color(0xFFE3E3DF),
            accent = Color(0xFFB9BEC3),
            primaryHairline = Color(0x99B9BEC3),
            cardShape = RoundedCornerShape(14.dp),
            buttonShape = RoundedCornerShape(12.dp),
            featureCardShape = RoundedCornerShape(14.dp),
            vehicleRes = R.drawable.car_side,
        )
        val Forest = CarTokens(
            id = AppThemeId.Forest,
            label = "포레스트",
            description = "그린 · 아이보리 · 스톤",
            primary = Color(0xFF203F36),
            black = Color(0xFF1C201E),
            white = Color(0xFFF7F5EE),
            surface = Color(0xFFECEDE3),
            textSecondary = Color(0xFF5E655F),
            inactive = Color(0xFFADB1A7),
            border = Color(0xFFE1E0D5),
            accent = Color(0xFFCBCFC4),
            cardShape = RoundedCornerShape(14.dp),
            buttonShape = RoundedCornerShape(12.dp),
            featureCardShape = RoundedCornerShape(14.dp),
            vehicleRes = R.drawable.car_side,
        )
        val Espresso = CarTokens(
            id = AppThemeId.Espresso,
            label = "에스프레소",
            description = "브라운 · 웜 화이트 · 페블",
            primary = Color(0xFF3B302B),
            black = Color(0xFF221C19),
            white = Color(0xFFF8F4EF),
            surface = Color(0xFFEFE8E0),
            textSecondary = Color(0xFF6A615B),
            inactive = Color(0xFFB3AAA2),
            border = Color(0xFFE6DDD4),
            accent = Color(0xFFC9BEB4),
            cardShape = RoundedCornerShape(14.dp),
            buttonShape = RoundedCornerShape(12.dp),
            featureCardShape = RoundedCornerShape(14.dp),
            vehicleRes = R.drawable.car_side,
        )
        val Silver = CarTokens(
            id = AppThemeId.Silver,
            label = "실버",
            description = "블랙 · 화이트 · 얇은 실버",
            primary = Color(0xFF181818),
            black = Color(0xFF181818),
            white = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F4F4),
            textSecondary = Color(0xFF666666),
            inactive = Color(0xFFA8A8A8),
            border = Color(0xFFE4E4E4),
            accent = Color(0xFFB9BEC3),
            accentSilver = Color(0xFFB9BEC3),
            primaryHairline = Color(0xFFB9BEC3),
            gloss = false,
            cardShape = RoundedCornerShape(4.dp),
            buttonShape = RoundedCornerShape(4.dp),
            featureCardShape = CutCornerShape(topEnd = 6.dp, bottomStart = 6.dp),
            vehicleRes = R.drawable.car_steel,
            vehicleTinted = false,
        )

        /** 블랙·차콜 프리미엄 다크 테크. 둥근 카드, 얇은 테두리, 은은한 그라데이션, 네온 핑크 포인트 */
        val Uhd = CarTokens(
            id = AppThemeId.Uhd,
            label = "UHD",
            description = "블랙 · 차콜 · 네온 핑크",
            primary = Color(0xFFFF2E88),
            black = Color(0xFFF1F1F4),
            white = Color(0xFF0B0B0E),
            surface = Color(0xFF17171C),
            textSecondary = Color(0xFF9D9EA8),
            inactive = Color(0xFF5B5C66),
            border = Color(0xFF2B2B33),
            backdrop = Color(0xA6000000),
            onPrimary = Color(0xFF0B0B0E),
            onPrimarySurface = Color(0xFFFF3D93),
            primaryBrush = Brush.verticalGradient(listOf(Color(0xFF2A2A32), Color(0xFF141418))),
            surfaceBrush = Brush.verticalGradient(listOf(Color(0xFF1D1D23), Color(0xFF121216))),
            cardBorder = Color(0xFF2E2E37),
            elapsedGreen = Color(0xFF3FD08A),
            elapsedBurgundy = Color(0xFFFF5C7A),
            dark = true,
            dial = true,
            accent = Color(0xFF3A3A44),
            primaryHairline = Color(0x8CFF2E88),
            gloss = false,
            cardShape = RoundedCornerShape(20.dp),
            buttonShape = RoundedCornerShape(16.dp),
            featureCardShape = RoundedCornerShape(20.dp),
            vehicleRes = R.drawable.car_side,
        )

        val all = listOf(Graphite, Forest, Espresso, Silver, Uhd)

        fun of(id: AppThemeId) = when (id) {
            AppThemeId.Graphite -> Graphite
            AppThemeId.Forest -> Forest
            AppThemeId.Espresso -> Espresso
            AppThemeId.Silver -> Silver
            AppThemeId.Uhd -> Uhd
        }
    }
}

/** 주요 면: 팔레트 색 + 위쪽의 약한 광택 + 얇은 테두리. 글자 뒤는 거의 평평하게 둔다 */
fun Modifier.primarySurface(t: CarTokens, shape: Shape, enabled: Boolean = true): Modifier {
    if (!enabled) return background(t.border, shape)
    var m = t.primaryBrush?.let { background(it, shape) } ?: background(t.primary, shape)
    if (t.gloss) {
        m = m.background(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.42f to Color.Transparent),
            shape,
        )
    }
    t.primaryBorder?.let { m = m.border(it, shape) }
    return m
}

/** 보조 카드 면: 단색 또는 UHD의 은은한 그라데이션 + 얇은 테두리 */
fun Modifier.cardSurface(t: CarTokens, shape: Shape): Modifier {
    var m = t.surfaceBrush?.let { background(it, shape) } ?: background(t.surface, shape)
    t.cardBorder?.let { m = m.border(1.dp, it, shape) }
    return m
}

/**
 * 주차 경과 시간 색. 2시간부터 진한 초록, 3시간부터 버건디.
 * 밝은 네 테마의 바탕에서 모두 본문 대비 4.5:1 이상이다. UHD는 어두운 바탕용 밝은 초록·로즈를 쓴다.
 */
object ElapsedTone {
    const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
    const val THREE_HOURS_MS = 3 * 60 * 60 * 1000L
    val Green = Color(0xFF1E7A4C)
    val Burgundy = Color(0xFF8E2A3B)

    fun color(elapsedMs: Long, normal: Color): Color = when {
        elapsedMs >= THREE_HOURS_MS -> Burgundy
        elapsedMs >= TWO_HOURS_MS -> Green
        else -> normal
    }

    fun color(elapsedMs: Long, t: CarTokens, normal: Color = t.black): Color = when {
        elapsedMs >= THREE_HOURS_MS -> t.elapsedBurgundy
        elapsedMs >= TWO_HOURS_MS -> t.elapsedGreen
        else -> normal
    }
}

object MapColors {
    val background = Color(0xFFF7F8F1)
    val land = Color(0xFFF2F5EC)
    val road = Color(0xFFFFFFFF)
    val roadEdge = Color(0xFFE5EBE0)
    val label = Color(0xFF798479)
    val currentPosition = Color(0xFF5483D5)
}

object CarType {
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 16.sp)
    val secondary = TextStyle(fontSize = 14.sp)
    val label = TextStyle(fontSize = 12.sp)
    val elapsed = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
    val homeFloor = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 48.sp)
    val selectedFloor = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
}

val LocalCarTokens = staticCompositionLocalOf { CarTokens.Graphite }

@Composable
fun CarTheme(themeId: AppThemeId, content: @Composable () -> Unit) {
    val tokens = CarTokens.of(themeId)
    // 시스템 다크 모드를 따르지 않는다. 고른 테마가 밝은지(4종) 어두운지(UHD)만 따른다
    val scheme = if (tokens.dark) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            background = tokens.white,
            onBackground = tokens.black,
            surface = tokens.surface,
            onSurface = tokens.black,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            secondary = tokens.textSecondary,
        )
    } else {
        lightColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            background = tokens.white,
            onBackground = tokens.black,
            surface = tokens.white,
            onSurface = tokens.black,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            secondary = tokens.textSecondary,
        )
    }
    CompositionLocalProvider(LocalCarTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
