package app.car.parking.platform.statusbar

import app.car.parking.domain.floor.Floors

/**
 * 층 표지판 색. 상태바 아이콘과 위젯이 같은 규칙을 쓴다.
 *
 * 글자는 항상 흰색이고 층마다 바탕색이 바뀐다. 지상·지하 모두 같은 순서로 반복한다.
 *   1F·B1 검정 → 2F·B2 버건디 → 3F·B3 초록 → 4F·B4 검정 …
 * 층 미선택: 검은 바탕 흰 P
 */
data class FloorSign(val plate: Int, val text: Int, val edge: Int) {
    companion object {
        const val BLACK = 0xFF181818.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BURGUNDY = 0xFF8E2A3B.toInt()
        const val GREEN = 0xFF1E7A4C.toInt()

        private val PLATES = intArrayOf(BLACK, BURGUNDY, GREEN)

        /** 바탕 가장자리. 어두운 바탕이 어두운 상태바에서도 윤곽이 보이게 한다 */
        private const val EDGE = 0xFFB9BEC3.toInt()

        fun of(level: Int?): FloorSign {
            if (level == null || level == 0) return FloorSign(BLACK, WHITE, EDGE)
            // 1F·B1 → 0, 2F·B2 → 1, 3F·B3 → 2, 4F·B4 → 0 …
            val step = (kotlin.math.abs(level) - 1) % PLATES.size
            return FloorSign(PLATES[step], WHITE, EDGE)
        }

        fun of(label: String?): FloorSign = of(Floors.parse(label))
    }

    /** 알림·Live Updates 칩 강조색. 흰 글자는 밝은 화면에서 안 보이므로 바탕색을 쓴다 */
    val accent: Int get() = if (text == WHITE) plate else text
}
