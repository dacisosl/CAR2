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
        /** 실기기 테스트 전까지의 초안 값 */
        const val DEFAULT_RECONNECT_MS = 6_000L
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

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

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
