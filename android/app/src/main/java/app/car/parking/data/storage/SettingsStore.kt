package app.car.parking.data.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 연결 이벤트 순서 판단에 쓰는 시간 창. 이보다 크게 어긋나면 시계가 바뀐 것으로 보고 새 이벤트를 받는다 */
private const val ORDER_WINDOW_MS = 60_000L

/** 설정에서 고르는 5가지 디자인. 저장 키는 바꾸지 않는다(이전 ‘classic’은 그래파이트로 이어진다) */
enum class AppThemeId(val key: String) {
    Graphite("graphite"), Forest("forest"), Espresso("espresso"), Silver("steel"), Uhd("uhd");

    companion object {
        /** 값 없음·알 수 없는 값은 그래파이트. 이전 버전의 클래식은 그래파이트로 옮긴다 */
        fun from(value: String?): AppThemeId = when (value) {
            "classic" -> Graphite
            else -> entries.firstOrNull { it.key == value } ?: Graphite
        }
    }
}

/** RTL과 무관한 물리적 왼쪽/오른쪽 */
enum class DrawerSide(val key: String) {
    Left("left"), Right("right");

    fun opposite() = if (this == Left) Right else Left

    companion object {
        fun from(value: String?): DrawerSide = entries.firstOrNull { it.key == value } ?: Left
    }
}

data class AppSettings(
    val schemaVersion: Int = 1,
    val registeredVehicleAddress: String? = null,
    val registeredVehicleName: String? = null,
    val autoRecordRequested: Boolean = true,
    val drawerSide: DrawerSide = DrawerSide.Left,
    val appTheme: AppThemeId = AppThemeId.Graphite,
    val onboardingCompleted: Boolean = false,
    val reconnectCheckMs: Long = DEFAULT_RECONNECT_MS,
    val lastConnectedAt: Long = 0L,
    /** 등록 차량이 지금 연결돼 있는지(마지막 ACL 이벤트·실제 프로필 확인으로 갱신). 홈의 ‘이동 중’ 표시 */
    val vehicleConnected: Boolean = false,
    /** 마지막 연결·해제 이벤트 시각 */
    val lastVehicleEventAt: Long = 0L,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
) {
    val hasHome: Boolean get() = homeLatitude != null && homeLongitude != null

    companion object {
        /** 시동을 끄고 내리면 2초 안에 패널이 뜨도록 한다. 이 시간 안의 재연결(시동 재시동)은 주차로 보지 않는다 (2026-10-04 사용자 결정) */
        const val DEFAULT_RECONNECT_MS = 2_000L
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val schema = intPreferencesKey("schemaVersion")
        val vehicleAddress = stringPreferencesKey("registeredVehicleId")
        val vehicleName = stringPreferencesKey("registeredVehicleName")
        val drawerSide = stringPreferencesKey("drawerSide")
        val appTheme = stringPreferencesKey("appTheme")
        val onboarding = booleanPreferencesKey("onboardingCompleted")
        val reconnectMs = longPreferencesKey("reconnectCheckMs")
        val lastConnectedAt = longPreferencesKey("lastConnectedAt")
        val vehicleConnected = booleanPreferencesKey("vehicleConnected")
        val lastVehicleEventAt = longPreferencesKey("lastVehicleEventAt")
        val homeLat = doublePreferencesKey("homeLatitude")
        val homeLng = doublePreferencesKey("homeLongitude")
    }

    /** 이 프로세스에서 마지막으로 읽은 설정(수신기가 이미 읽었으면 채워져 있다). 첫 프레임 초기값 전용 */
    @Volatile var cached: AppSettings? = null
        private set

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings().also { s -> cached = s } }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        schemaVersion = this[Keys.schema] ?: 1,
        registeredVehicleAddress = this[Keys.vehicleAddress],
        registeredVehicleName = this[Keys.vehicleName],
        // 자동 기록은 항상 켜짐(설정 스위치 없음). 이전 버전에서 끈 값도 다시 켠다
        autoRecordRequested = true,
        drawerSide = DrawerSide.from(this[Keys.drawerSide]),
        appTheme = AppThemeId.from(this[Keys.appTheme]),
        onboardingCompleted = this[Keys.onboarding] ?: false,
        reconnectCheckMs = this[Keys.reconnectMs] ?: AppSettings.DEFAULT_RECONNECT_MS,
        lastConnectedAt = this[Keys.lastConnectedAt] ?: 0L,
        vehicleConnected = this[Keys.vehicleConnected] ?: false,
        lastVehicleEventAt = this[Keys.lastVehicleEventAt] ?: 0L,
        homeLatitude = this[Keys.homeLat],
        homeLongitude = this[Keys.homeLng],
    )

    suspend fun setTheme(theme: AppThemeId) {
        context.dataStore.edit { it[Keys.appTheme] = theme.key }
    }

    suspend fun setDrawerSide(side: DrawerSide) {
        context.dataStore.edit { it[Keys.drawerSide] = side.key }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[Keys.onboarding] = true }
    }

    suspend fun setLastConnectedAt(time: Long) {
        context.dataStore.edit { it[Keys.lastConnectedAt] = time }
    }

    /** 차량 연결 상태. [atMs]가 0이면 이벤트 시각은 바꾸지 않는다(실제 상태 재확인) */
    suspend fun setVehicleLink(connected: Boolean, atMs: Long = 0L) {
        context.dataStore.edit {
            // 조금 더 최근 이벤트가 이미 저장돼 있으면 늦게 도착한 이전 이벤트로 덮지 않는다.
            // 차이가 크면(시계가 뒤로 맞춰진 경우 등) 순서 판단에 쓰지 않고 새 이벤트를 받는다
            val stored = it[Keys.lastVehicleEventAt] ?: 0L
            if (atMs > 0L && atMs < stored && stored - atMs < ORDER_WINDOW_MS) return@edit
            it[Keys.vehicleConnected] = connected
            if (atMs > 0L) it[Keys.lastVehicleEventAt] = atMs
        }
    }

    suspend fun setVehicle(address: String, name: String?) {
        context.dataStore.edit {
            if (it[Keys.vehicleAddress] != address) {
                // 다른 차량으로 바꾸면 이전 차량의 연결 세션·연결 상태를 쓰지 않는다
                it.remove(Keys.lastConnectedAt)
                it.remove(Keys.vehicleConnected)
                it.remove(Keys.lastVehicleEventAt)
            }
            it[Keys.vehicleAddress] = address
            if (name != null) it[Keys.vehicleName] = name else it.remove(Keys.vehicleName)
        }
    }

    suspend fun setHome(latitude: Double, longitude: Double) {
        context.dataStore.edit {
            it[Keys.homeLat] = latitude
            it[Keys.homeLng] = longitude
        }
    }

    suspend fun clearHome() {
        context.dataStore.edit {
            it.remove(Keys.homeLat)
            it.remove(Keys.homeLng)
        }
    }
}
