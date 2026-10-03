package app.car.parking.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.car.parking.R
import app.car.parking.data.storage.AppThemeId

/** design/tokens.json의 themes.classic / themes.steel */
@Immutable
data class CarTokens(
    val id: AppThemeId,
    val black: Color = Color(0xFF181818),
    val white: Color = Color(0xFFFFFFFF),
    val surface: Color = Color(0xFFF4F4F4),
    val textSecondary: Color = Color(0xFF666666),
    val inactive: Color = Color(0xFFA8A8A8),
    val border: Color = Color(0xFFE4E4E4),
    val backdrop: Color = Color(0x47181818),
    val onPrimary: Color = Color.White,
    /** 스틸 포인트 전용. 얇은 테두리·손잡이·차량 선에만 사용 */
    val accentSilver: Color? = null,
    val cardShape: Shape,
    val buttonShape: Shape,
    /** 스틸 포인트의 일부 카드에 쓰는 작은 사선 모서리 */
    val featureCardShape: Shape,
    @param:DrawableRes val vehicleRes: Int,
) {
    val isSteel get() = id == AppThemeId.Steel

    /** 선택 층수·저장 같은 검정 면의 테두리. 클래식은 없음 */
    val primaryBorder: BorderStroke? get() = accentSilver?.let { BorderStroke(1.dp, it) }

    companion object {
        val Classic = CarTokens(
            id = AppThemeId.Classic,
            cardShape = RoundedCornerShape(16.dp),
            buttonShape = RoundedCornerShape(12.dp),
            featureCardShape = RoundedCornerShape(16.dp),
            vehicleRes = R.drawable.car_side,
        )
        val Steel = CarTokens(
            id = AppThemeId.Steel,
            accentSilver = Color(0xFFB9BEC3),
            cardShape = RoundedCornerShape(4.dp),
            buttonShape = RoundedCornerShape(4.dp),
            featureCardShape = CutCornerShape(topEnd = 6.dp, bottomStart = 6.dp),
            vehicleRes = R.drawable.car_steel,
        )

        fun of(id: AppThemeId) = if (id == AppThemeId.Steel) Steel else Classic
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

val LocalCarTokens = staticCompositionLocalOf { CarTokens.Classic }

@Composable
fun CarTheme(themeId: AppThemeId, content: @Composable () -> Unit) {
    val tokens = CarTokens.of(themeId)
    // 시스템 다크 모드를 따르지 않는다. 두 테마 모두 밝은 화면이다
    val scheme = lightColorScheme(
        primary = tokens.black,
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
