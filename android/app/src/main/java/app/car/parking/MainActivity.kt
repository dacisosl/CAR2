package app.car.parking

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.ui.AppViewModel
import app.car.parking.ui.CarRoot

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    /** 자동 진입이 이 앱을 뒤에서(또는 꺼진 화면에서) 앞으로 꺼냈는지 */
    private var broughtForwardByAutoEntry = false

    /**
     * 마지막으로 앞에 보인 뒤 화면이 가려졌는지(onStop). 새로 만든 화면은 보인 적이 없으므로 true.
     * 뒤에 있던 화면에 새 인텐트가 오면 시스템이 onStart를 먼저 부르므로 수명 주기 상태로는 구분할 수 없다
     */
    private var stoppedSinceResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 다크 모드와 관계없이 시작은 어두운 아이콘. 어두운 UHD 테마에서는 CarRoot가 밝은 아이콘으로 바꾼다
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleEntry(intent)
        setContent { CarRoot(viewModel, onUnlockThen = ::unlockThen) }
        // 잠금 화면 위 표시는 자동 진입을 여는 중이거나 자동 진입 패널이 떠 있는 동안만 유지한다.
        // 후보가 이미 처리돼 패널이 열리지 않으면 바로 꺼진다
        lifecycleScope.launch {
            viewModel.lockScreenEntry.collect(::setLockScreenEntry)
        }
        // 시동만 껐다 켜서 자동으로 띄운 패널이 저절로 닫히면, 쓰던 앱(내비게이션 등)이나 잠금 화면으로 돌아간다
        lifecycleScope.launch {
            viewModel.autoEntryAbandoned.collect {
                if (broughtForwardByAutoEntry) {
                    broughtForwardByAutoEntry = false
                    Log.i("AutoEntry", "auto entry ended without user input — returning to the previous app")
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEntry(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedSinceResume = true
    }

    override fun onResume() {
        super.onResume()
        stoppedSinceResume = false
        // 권한·설정 재확인 + 실제 차량 연결 상태 보정 + 아직 보여 주지 않은 하차 후보 표시
        viewModel.onForeground()
    }

    private fun handleEntry(intent: Intent) {
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val auto = !fromHistory && intent.getBooleanExtra(AutoLauncher.EXTRA_AUTO_ENTRY, false)
        if (fromHistory) return
        // 보조 알림을 눌러 연 경우는 사용자가 연 것이므로 제외한다
        val viaNotification = intent.getBooleanExtra(AutoLauncher.EXTRA_VIA_NOTIFICATION, false)
        broughtForwardByAutoEntry = auto && !viaNotification && stoppedSinceResume
        // 자동 진입일 때만 잠금 화면 위 표시·화면 켜기. 보안 잠금 해제는 우회하지 않는다
        setLockScreenEntry(auto)
        viewModel.handleEntry(
            candidateId = intent.getStringExtra(AutoLauncher.EXTRA_CANDIDATE_ID),
            openPanel = intent.getBooleanExtra(AutoLauncher.EXTRA_OPEN_PANEL, false),
            autoEntry = auto,
        )
    }

    @Suppress("DEPRECATION")
    fun setLockScreenEntry(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enabled) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    /**
     * 사진 선택·촬영처럼 인증이 필요한 동작 전에 정상 시스템 잠금 해제를 요청한다.
     * 잠겨 있지 않으면 바로 실행한다.
     */
    private fun unlockThen(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            action()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = action()
        })
    }
}
