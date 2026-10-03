package app.car.parking.domain.floor

/**
 * 층 표기 규칙. 1=1F, 2=2F, -1=B1, -2=B2. 0은 사용하지 않는다.
 * 계산은 연속 인덱스(1F=0, B1=-1)로 하고 표기할 때만 0F를 건너뛴다.
 */
object Floors {
    const val DEFAULT_TOP = 6
    const val DEFAULT_BOTTOM = -6

    /** 릴 순서: 높은 지상층 → 1F → B1 → 더 깊은 지하층 */
    fun reel(top: Int = DEFAULT_TOP, bottom: Int = DEFAULT_BOTTOM): List<Int> =
        (top downTo bottom).filter { it != 0 }

    fun label(level: Int?): String? = when {
        level == null || level == 0 -> null
        level > 0 -> "${level}F"
        else -> "B${-level}"
    }

    fun parse(label: String?): Int? {
        val text = label?.trim()?.uppercase() ?: return null
        return when {
            text.startsWith("B") -> text.drop(1).toIntOrNull()?.takeIf { it > 0 }?.let { -it }
            text.endsWith("F") -> text.dropLast(1).toIntOrNull()?.takeIf { it > 0 }
            else -> null
        }
    }

    fun toIndex(level: Int): Int {
        require(level != 0) { "0F는 없다" }
        return if (level > 0) level - 1 else level
    }

    fun fromIndex(index: Int): Int = if (index >= 0) index + 1 else index

    /** 기준층에서 [delta]층 이동한 층. 1F↔B1 전이에서 0F를 만들지 않는다. */
    fun shift(level: Int, delta: Int): Int = fromIndex(toIndex(level) + delta)
}
