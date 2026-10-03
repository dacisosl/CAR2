package app.car.parking.data.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 설정에서 고르는 4가지 디자인. 저장 키는 바꾸지 않는다(이전 ‘classic’은 그래파이트로 이어진다) */
enum class AppThemeId(val key: String) {
    Graphite("graphite"), Forest("forest"), Espresso("espresso"), Silver("steel");

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
    val statusBarEnabled: Boolean = true,
    val reconnectCheckMs: Long = DEFAULT_RECONNECT_MS,
    val autoLaunchTestPassedAt: Long = 0L,
    val autoLaunchTestDevice: String? = null,
    val autoLaunchTestStartedAt: Long = 0L,
    val lastConnectedAt: Long = 0L,
) {
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
        val autoRecord = booleanPreferencesKey("autoRecordRequested")
        val drawerSide = stringPreferencesKey("drawerSide")
        val appTheme = stringPreferencesKey("appTheme")
        val onboarding = booleanPreferencesKey("onboardingCompleted")
        val statusBar = booleanPreferencesKey("statusBarEnabled")
        val reconnectMs = longPreferencesKey("reconnectCheckMs")
        val testPassedAt = longPreferencesKey("autoLaunchTestPassedAt")
        val testDevice = stringPreferencesKey("autoLaunchTestDevice")
        val testStartedAt = longPreferencesKey("autoLaunchTestStartedAt")
        val lastConnectedAt = longPreferencesKey("lastConnectedAt")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        schemaVersion = this[Keys.schema] ?: 1,
        registeredVehicleAddress = this[Keys.vehicleAddress],
        registeredVehicleName = this[Keys.vehicleName],
        autoRecordRequested = this[Keys.autoRecord] ?: true,
        drawerSide = DrawerSide.from(this[Keys.drawerSide]),
        appTheme = AppThemeId.from(this[Keys.appTheme]),
        onboardingCompleted = this[Keys.onboarding] ?: false,
        statusBarEnabled = this[Keys.statusBar] ?: true,
        reconnectCheckMs = this[Keys.reconnectMs] ?: AppSettings.DEFAULT_RECONNECT_MS,
        autoLaunchTestPassedAt = this[Keys.testPassedAt] ?: 0L,
        autoLaunchTestDevice = this[Keys.testDevice],
        autoLaunchTestStartedAt = this[Keys.testStartedAt] ?: 0L,
        lastConnectedAt = this[Keys.lastConnectedAt] ?: 0L,
    )

    suspend fun setTheme(theme: AppThemeId) {
        context.dataStore.edit { it[Keys.appTheme] = theme.key }
    }

    suspend fun setDrawerSide(side: DrawerSide) {
        context.dataStore.edit { it[Keys.drawerSide] = side.key }
    }

    suspend fun setAutoRecord(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoRecord] = enabled }
    }

    suspend fun setStatusBar(enabled: Boolean) {
        context.dataStore.edit { it[Keys.statusBar] = enabled }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[Keys.onboarding] = true }
    }

    suspend fun setLastConnectedAt(time: Long) {
        context.dataStore.edit { it[Keys.lastConnectedAt] = time }
    }

    suspend fun setVehicle(address: String, name: String?) {
        context.dataStore.edit {
            if (it[Keys.vehicleAddress] != address) {
                // 다른 차량으로 바꾸면 이전 차량 기준의 테스트 결과·연결 세션을 쓰지 않는다
                it.remove(Keys.testPassedAt)
                it.remove(Keys.testDevice)
                it.remove(Keys.lastConnectedAt)
            }
            it[Keys.vehicleAddress] = address
            if (name != null) it[Keys.vehicleName] = name else it.remove(Keys.vehicleName)
        }
    }

    suspend fun markAutoLaunchTestStarted(time: Long) {
        context.dataStore.edit { it[Keys.testStartedAt] = time }
    }

    suspend fun markAutoLaunchTestPassed(time: Long, device: String) {
        context.dataStore.edit {
            it[Keys.testPassedAt] = time
            it[Keys.testDevice] = device
            it[Keys.testStartedAt] = 0L
        }
    }

    suspend fun clearAutoLaunchTestStarted() {
        context.dataStore.edit { it[Keys.testStartedAt] = 0L }
    }
}
