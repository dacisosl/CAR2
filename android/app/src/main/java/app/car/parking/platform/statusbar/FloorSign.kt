package app.car.parking.platform.statusbar

import app.car.parking.domain.floor.Floors

/**
 * 층 표지판 색. 상태바 아이콘과 위젯이 같은 규칙을 쓴다.
 *
 * 지상(1F 이상): 검은 바탕, 글자 흰색 → 초록 → 실버 반복 (1F 흰, 2F 초록, 3F 실버, 4F 흰 …)
 * 지하(B1 이하): 흰 바탕, 글자 검정 → 초록 → 실버 반복 (B1 검정, B2 초록, B3 실버, B4 검정 …)
 * 층 미선택: 검은 바탕 흰 P
 *
 * 초록·실버는 바탕에 따라 밝기를 달리해 글자가 읽히게 한다.
 */
data class FloorSign(val plate: Int, val text: Int, val edge: Int) {
    companion object {
        const val BLACK = 0xFF181818.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()

        // 검은 바탕용
        const val GREEN_ON_BLACK = 0xFF3DDC84.toInt()
        const val SILVER_ON_BLACK = 0xFFC3C8CD.toInt()

        // 흰 바탕용
        const val GREEN_ON_WHITE = 0xFF1E7A4C.toInt()
        const val SILVER_ON_WHITE = 0xFF858B92.toInt()

        /** 바탕 가장자리. 흰 바탕은 밝은 상태바, 검은 바탕은 어두운 상태바에서도 윤곽이 보이게 한다 */
        private const val EDGE = 0xFFB9BEC3.toInt()

        fun of(level: Int?): FloorSign {
            if (level == null || level == 0) return FloorSign(BLACK, WHITE, EDGE)
            val step = Floors.toIndex(level).let { if (it >= 0) it else -it - 1 } % 3
            return if (level > 0) {
                FloorSign(BLACK, intArrayOf(WHITE, GREEN_ON_BLACK, SILVER_ON_BLACK)[step], EDGE)
            } else {
                FloorSign(WHITE, intArrayOf(BLACK, GREEN_ON_WHITE, SILVER_ON_WHITE)[step], EDGE)
            }
        }

        fun of(label: String?): FloorSign = of(Floors.parse(label))
    }

    /** 알림·Live Updates 칩 강조색. 흰 글자는 밝은 화면에서 안 보이므로 바탕색을 쓴다 */
    val accent: Int get() = if (text == WHITE) plate else text
}
