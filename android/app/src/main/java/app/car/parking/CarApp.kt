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
import com.naver.maps.map.NaverMapSdk

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val db = ParkingDatabase.create(context)
    val parking = ParkingRepository(db.dao(), settings)
    val location = LocationRepository(context)
    val pressure = PressureSampler(context)
    val photos = PhotoStore(context)

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
        if (BuildConfig.NAVER_MAP_KEY_ID.isNotBlank()) {
            NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_KEY_ID)
        }
    }
}
