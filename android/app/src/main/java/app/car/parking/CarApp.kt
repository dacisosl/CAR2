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
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.platform.statusbar.StatusBarNotifier
import app.car.parking.platform.update.SelfUpdater
import app.car.parking.platform.update.SelfUpdaters
import app.car.parking.platform.widget.ParkingWidgets
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
    /** GitHub 배포판만 있다. Play 빌드는 null */
    val updater: SelfUpdater? = SelfUpdaters.create(context)

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
        AutoLauncher.createChannel(this)
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
        // 네이버 지도 SDK 클라이언트는 지도를 처음 만들 때 설정한다(ParkingMap). 수신기만 깨어날 때 비용을 내지 않는다
    }
}
