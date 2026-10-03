package app.car.parking.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

        val all = listOf(Graphite, Forest, Espresso, Silver)

        fun of(id: AppThemeId) = when (id) {
            AppThemeId.Graphite -> Graphite
            AppThemeId.Forest -> Forest
            AppThemeId.Espresso -> Espresso
            AppThemeId.Silver -> Silver
        }
    }
}

/** 주요 면: 팔레트 색 + 위쪽의 약한 광택 + 얇은 테두리. 글자 뒤는 거의 평평하게 둔다 */
fun Modifier.primarySurface(t: CarTokens, shape: Shape, enabled: Boolean = true): Modifier {
    if (!enabled) return background(t.border, shape)
    var m = background(t.primary, shape)
    if (t.gloss) {
        m = m.background(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.42f to Color.Transparent),
            shape,
        )
    }
    t.primaryBorder?.let { m = m.border(it, shape) }
    return m
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
    // 시스템 다크 모드를 따르지 않는다. 네 테마 모두 밝은 화면이다
    val scheme = lightColorScheme(
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
    CompositionLocalProvider(LocalCarTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
