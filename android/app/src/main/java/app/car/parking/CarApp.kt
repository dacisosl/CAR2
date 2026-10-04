package app.car.parking

import android.app.Application
import android.content.Context
import app.car.parking.data.location.LocationRepository
import app.car.parking.data.sensor.PressureSampler
import app.car.parking.data.storage.ParkingDatabase
import app.car.parking.data.storage.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.car.parking.data.storage.SettingsStore
import app.car.parking.domain.parking.ParkingRepository
import app.car.parking.platform.statusbar.StatusBarNotifier
import app.car.parking.platform.update.AppUpdater
import app.car.parking.platform.widget.ParkingWidgets
import com.naver.maps.map.NaverMapSdk
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val db = ParkingDatabase.create(context)
    val parking = ParkingRepository(db.dao(), settings)
    val location = LocationRepository(context)
    val pressure = PressureSampler(context)
    val photos = PhotoStore(context)
    val updater = AppUpdater(context)

    /** 화면 수명과 무관하게 끝나야 하는 짧은 작업(자동 표시 테스트 예약 등) */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class CarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        StatusBarNotifier.createChannel(this)
        // 기록·테마가 바뀌면 위젯과 상태바 표시를 함께 맞춘다(저장·위치 저장·사진·테마 변경 포함)
        container.appScope.launch {
            combine(
                container.parking.latestRecord,
                container.settings.settings.map { it.appTheme to it.vehicleConnected },
            ) { record, look -> record to look }
                .distinctUntilChanged()
                .collect {
                    StatusBarNotifier.refresh(this@CarApp)
                    ParkingWidgets.updateAll(this@CarApp)
                }
        }
        if (BuildConfig.NAVER_MAP_KEY_ID.isNotBlank()) {
            NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_KEY_ID)
        }
    }
}
